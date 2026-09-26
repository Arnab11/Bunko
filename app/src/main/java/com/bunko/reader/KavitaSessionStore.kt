package com.bunko.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("kavita_session")

data class KavitaSession(
    val baseUrl: String = "",
    val username: String = "",
    val apiKey: String = "",
    val jwt: String = ""
)

data class KavitaServerProfile(
    val id: String,
    val name: String,
    val session: KavitaSession,
    val openByDefault: Boolean
)

data class KavitaSessionStorageState(
    val hasSavedAuth: Boolean,
    val secretsEncrypted: Boolean,
    val hasLegacyPlaintextSecrets: Boolean
)

@Serializable
private data class StoredServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String = "",
    val apiKey: String = "",
    val jwt: String = "",
    val openByDefault: Boolean = false
)

class KavitaSessionStore(private val context: Context) {
    private val KEY_BASE_URL = stringPreferencesKey("baseUrl")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_API_KEY = stringPreferencesKey("apiKey")
    private val KEY_JWT = stringPreferencesKey("jwt")
    private val KEY_PROFILES = stringPreferencesKey("serverProfiles")

    private val json = Json { ignoreUnknownKeys = true }
    private var transientSession: KavitaSession? = null
    private var activeProfileId: String? = null

    // The resolved active session is decrypted from DataStore, which costs several
    // Android KeyStore (TEE) round-trips per call. load() runs on every HTTP request
    // (the auth-header interceptor), so cache the decrypted result and re-resolve only
    // after a write or a profile switch invalidates it.
    @Volatile
    private var cachedActiveSession: KavitaSession? = null

    fun useTransient(session: KavitaSession) {
        transientSession = session.normalized()
    }

    suspend fun load(): KavitaSession {
        transientSession?.let { return it }
        cachedActiveSession?.let { return it }
        val resolved = activeProfile()?.session ?: KavitaSession()
        cachedActiveSession = resolved
        return resolved
    }

    suspend fun loadDefault(): KavitaSession {
        transientSession?.let { return it }
        return profiles().firstOrNull { it.openByDefault }?.session ?: KavitaSession()
    }

    suspend fun profiles(): List<KavitaServerProfile> {
        val prefs = context.applicationContext.dataStore.data.first()
        val stored = decodeStoredProfiles(prefs[KEY_PROFILES])
        if (stored.isNotEmpty()) {
            return stored.map { it.toProfile() }
        }

        val legacy = legacyProfileOrNull(
            baseUrl = prefs[KEY_BASE_URL] ?: "",
            username = prefs[KEY_USERNAME] ?: "",
            apiKey = prefs[KEY_API_KEY] ?: "",
            jwt = prefs[KEY_JWT] ?: ""
        ) ?: return emptyList()

        writeProfiles(listOf(legacy))
        return listOf(legacy)
    }

    suspend fun activeProfile(): KavitaServerProfile? {
        val all = profiles()
        return all.firstOrNull { it.id == activeProfileId }
            ?: all.firstOrNull { it.openByDefault }
            ?: all.firstOrNull()
    }

    fun selectProfile(id: String?) {
        activeProfileId = id
        transientSession = null
        cachedActiveSession = null
    }

    suspend fun save(session: KavitaSession, rememberAuth: Boolean = true) {
        saveProfile(activeProfileId, session, rememberAuth)
    }

    suspend fun saveProfile(
        profileId: String?,
        session: KavitaSession,
        rememberAuth: Boolean = true,
        openByDefault: Boolean? = null
    ): KavitaServerProfile {
        val normalized = session.normalized()
        val existingProfiles = profiles()
        val existing = existingProfiles.firstOrNull { it.id == profileId }
            ?: existingProfiles.firstOrNull { it.matchesServerIdentity(normalized) }
        val savedSession = if (rememberAuth) {
            normalized
        } else {
            normalized.copy(username = "", apiKey = "", jwt = "")
        }
        val savedProfile = KavitaServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = profileName(normalized.baseUrl, normalized.username),
            session = savedSession,
            openByDefault = openByDefault ?: existing?.openByDefault ?: existingProfiles.none { it.openByDefault }
        )
        val nextProfiles = existingProfiles
            .filterNot { it.id == savedProfile.id || it.matchesServerIdentity(normalized) }
            .plus(savedProfile)
            .normalizeDefault(savedProfile.id, savedProfile.openByDefault)

        activeProfileId = savedProfile.id
        transientSession = normalized
        writeProfiles(nextProfiles)
        return savedProfile
    }

    suspend fun setOpenByDefault(profileId: String, openByDefault: Boolean) {
        val nextProfiles = profiles().map { profile ->
            when {
                profile.id == profileId -> profile.copy(openByDefault = openByDefault)
                openByDefault -> profile.copy(openByDefault = false)
                else -> profile
            }
        }
        writeProfiles(nextProfiles)
    }

    suspend fun setDefaultProfile(profileId: String) {
        setOpenByDefault(profileId, true)
    }

    suspend fun clearJwt() {
        clearCredentials(activeProfileId, clearUsername = false, clearApiKey = false)
    }

    suspend fun clearCredentials(profileId: String? = activeProfileId, clearUsername: Boolean = true, clearApiKey: Boolean = true) {
        val targetId = profileId ?: activeProfile()?.id ?: return
        val nextProfiles = profiles().map { profile ->
            if (profile.id != targetId) return@map profile
            profile.copy(
                session = profile.session.copy(
                    username = if (clearUsername) "" else profile.session.username,
                    apiKey = if (clearApiKey) "" else profile.session.apiKey,
                    jwt = ""
                )
            )
        }
        if (activeProfileId == targetId) {
            transientSession = transientSession?.copy(
                username = if (clearUsername) "" else transientSession?.username.orEmpty(),
                apiKey = if (clearApiKey) "" else transientSession?.apiKey.orEmpty(),
                jwt = ""
            )
        }
        writeProfiles(nextProfiles)
    }

    suspend fun clearAll() {
        deleteProfile(activeProfileId ?: activeProfile()?.id)
    }

    suspend fun deleteProfile(profileId: String?) {
        val targetId = profileId ?: return
        val nextProfiles = profiles().filterNot { it.id == targetId }
        if (activeProfileId == targetId) {
            activeProfileId = nextProfiles.firstOrNull { it.openByDefault }?.id ?: nextProfiles.firstOrNull()?.id
            transientSession = null
        }
        writeProfiles(nextProfiles)
    }

    suspend fun storageState(profileId: String? = activeProfileId): KavitaSessionStorageState {
        val targetId = profileId ?: activeProfile()?.id
        val stored = decodeStoredProfiles(context.applicationContext.dataStore.data.first()[KEY_PROFILES])
        val profile = stored.firstOrNull { it.id == targetId } ?: stored.firstOrNull()
        if (profile != null) {
            val secrets = listOf(profile.apiKey, profile.jwt).filter { it.isNotBlank() }
            val encryptedValues = listOf(profile.username, profile.apiKey, profile.jwt).filter { it.isNotBlank() }
            return KavitaSessionStorageState(
                hasSavedAuth = secrets.isNotEmpty(),
                secretsEncrypted = encryptedValues.isNotEmpty() && encryptedValues.all { SecureSessionCipher.isEncrypted(it) },
                hasLegacyPlaintextSecrets = encryptedValues.any { it.isLegacySecret() }
            )
        }

        val prefs = context.applicationContext.dataStore.data.first()
        val rawUsername = prefs[KEY_USERNAME] ?: ""
        val rawApiKey = prefs[KEY_API_KEY] ?: ""
        val rawJwt = prefs[KEY_JWT] ?: ""
        val secrets = listOf(rawApiKey, rawJwt).filter { it.isNotBlank() }
        val encryptedValues = listOf(rawUsername, rawApiKey, rawJwt).filter { it.isNotBlank() }
        return KavitaSessionStorageState(
            hasSavedAuth = secrets.isNotEmpty(),
            secretsEncrypted = encryptedValues.isNotEmpty() && encryptedValues.all { SecureSessionCipher.isEncrypted(it) },
            hasLegacyPlaintextSecrets = encryptedValues.any { it.isLegacySecret() }
        )
    }

    private suspend fun writeProfiles(profiles: List<KavitaServerProfile>) {
        // Any profile write can change the active session (token refresh, credential
        // edit, delete); drop the cache so the next load() re-resolves from disk.
        cachedActiveSession = null
        val stored = profiles.map { it.toStored() }
        context.applicationContext.dataStore.edit { prefs ->
            prefs[KEY_PROFILES] = json.encodeToString(ListSerializer(StoredServerProfile.serializer()), stored)
            prefs[KEY_BASE_URL] = ""
            prefs[KEY_USERNAME] = ""
            prefs[KEY_API_KEY] = ""
            prefs[KEY_JWT] = ""
        }
        // Invalidate again after the edit commits: a load() that raced this write could
        // have re-cached the pre-write session while the edit was in flight.
        cachedActiveSession = null
    }

    private fun decodeStoredProfiles(value: String?): List<StoredServerProfile> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(StoredServerProfile.serializer()), value)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun legacyProfileOrNull(
        baseUrl: String,
        username: String,
        apiKey: String,
        jwt: String
    ): KavitaServerProfile? {
        val session = KavitaSession(
            baseUrl = baseUrl,
            username = SecureSessionCipher.decryptOrLegacy(username),
            apiKey = SecureSessionCipher.decryptOrLegacy(apiKey),
            jwt = SecureSessionCipher.decryptOrLegacy(jwt)
        ).normalized()
        if (session.baseUrl.isBlank() && session.username.isBlank() && session.apiKey.isBlank() && session.jwt.isBlank()) {
            return null
        }
        return KavitaServerProfile(
            id = UUID.randomUUID().toString(),
            name = profileName(session.baseUrl, session.username),
            session = session,
            openByDefault = session.baseUrl.isNotBlank() && (session.apiKey.isNotBlank() || session.jwt.isNotBlank())
        )
    }

    private fun StoredServerProfile.toProfile(): KavitaServerProfile {
        val session = KavitaSession(
            baseUrl = baseUrl,
            username = SecureSessionCipher.decryptOrLegacy(username),
            apiKey = SecureSessionCipher.decryptOrLegacy(apiKey),
            jwt = SecureSessionCipher.decryptOrLegacy(jwt)
        ).normalized()
        return KavitaServerProfile(
            id = id,
            name = name.ifBlank { profileName(session.baseUrl, session.username) },
            session = session,
            openByDefault = openByDefault
        )
    }

    private fun KavitaServerProfile.toStored(): StoredServerProfile {
        return StoredServerProfile(
            id = id,
            name = name,
            baseUrl = session.baseUrl,
            username = SecureSessionCipher.encrypt(session.username.trim()),
            apiKey = SecureSessionCipher.encrypt(session.apiKey.trim()),
            jwt = SecureSessionCipher.encrypt(session.jwt.trim()),
            openByDefault = openByDefault
        )
    }

    private fun List<KavitaServerProfile>.normalizeDefault(
        changedProfileId: String,
        changedProfileIsDefault: Boolean
    ): List<KavitaServerProfile> {
        return if (changedProfileIsDefault) {
            map { profile -> profile.copy(openByDefault = profile.id == changedProfileId) }
        } else {
            this
        }
    }

    private fun KavitaSession.normalized(): KavitaSession {
        return copy(baseUrl = baseUrl.trim().trimEnd('/'), username = username.trim(), apiKey = apiKey.trim(), jwt = jwt.trim())
    }

    private fun profileName(baseUrl: String, username: String): String {
        val server = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .ifBlank { "New Server" }
        return if (username.isBlank()) server else "$server / $username"
    }

    private fun KavitaServerProfile.matchesServerIdentity(session: KavitaSession): Boolean {
        if (session.baseUrl.isBlank() || this.session.baseUrl.isBlank()) return false
        if (!this.session.baseUrl.equals(session.baseUrl, ignoreCase = true)) return false
        return this.session.username.equals(session.username, ignoreCase = true) ||
            this.session.username.isBlank() ||
            session.username.isBlank()
    }

    private fun String.isLegacySecret(): Boolean {
        return isNotBlank() && !SecureSessionCipher.isEncrypted(this)
    }
}

private object SecureSessionCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bunko_kavita_session_v1"
    private const val PREFIX = "aesgcm-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun isEncrypted(value: String): Boolean = value.startsWith("$PREFIX:")

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$PREFIX:$iv:$payload"
    }

    fun decryptOrLegacy(value: String): String {
        if (value.isBlank()) return ""
        if (!isEncrypted(value)) return value
        val parts = value.split(":")
        require(parts.size == 3) { "Invalid encrypted session value" }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
