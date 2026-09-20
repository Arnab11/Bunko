package com.bunko.reader.reader.internal

import com.bunko.reader.FileDimensionDto
import com.bunko.reader.ReaderReadingDirection
import com.bunko.reader.SeriesMetadataDto

/**
 * Intelligent detector for Webtoon / Manhwa / Long Strip formats.
 * Inspects page aspect ratios, series titles, tags, genres, publishers, and metadata.
 */
object ReaderWebtoonDetector {

    private val WEBTOON_KEYWORDS = listOf(
        "webtoon",
        "webtoons",
        "manhwa",
        "manhua",
        "long strip",
        "longstrip",
        "vertical scroll"
    )

    private val WEBTOON_PLATFORMS = listOf(
        "kakaopage",
        "kakao",
        "tapas",
        "tappytoon",
        "webtoon",
        "naver webtoon",
        "naver",
        "lezhin",
        "toomics",
        "redice studio",
        "redice",
        "line webtoon",
        "piccoma",
        "bilibili",
        "asura",
        "asurascans",
        "reaper",
        "reaperscans",
        "flamescans",
        "flame comics",
        "voidscans",
        "void scans",
        "luminous scans",
        "alpha scans",
        "cosmic scans",
        "lucaz"
    )

    private val KNOWN_WEBTOON_TITLES = listOf(
        "solo leveling",
        "tower of god",
        "noblesse",
        "the beginning after the end",
        "omniscient reader",
        "lookism",
        "god of high school",
        "wind breaker",
        "nano machine",
        "return of the blossoming blade",
        "magic emperor",
        "eleceed",
        "legend of the northern blade",
        "mercenary enrollment",
        "viral hit",
        "how to fight",
        "sweet home",
        "bastard",
        "unordinary",
        "lore olympus",
        "overgeared",
        "greatest real estate developer",
        "greatest estate developer",
        "reincarnation of the suicidal battle god",
        "sss-class",
        "trash of the count",
        "pick me up",
        "doom breaker",
        "ranker who lives a second time",
        "tomb raider king",
        "villains are destined to die",
        "who made me a princess",
        "remarried empress",
        "leveling with the gods",
        "damn reincarnation",
        "reformation of the deadbeat noble",
        "swordmaster's youngest son",
        "player who can't level up",
        "murim login",
        "infinite leveling",
        "worthless regression",
        "return of the disaster-class hero",
        "standard of reincarnation",
        "talent-swallowing magician",
        "boundless necromancer",
        "auto-hunting",
        "reaper of the drifting moon",
        "dungeon reset",
        "survival story of a sword king",
        "return of the mad demon",
        "the boxer",
        "weak hero",
        "hardcore leveling warrior",
        "martial peak",
        "apotheosis",
        "tales of demons and gods",
        "hero killer",
        "study group",
        "get schooled",
        "manager kim",
        "questism",
        "reality quest",
        "juvenile offender",
        "true beauty",
        "unholy blood",
        "jungle juice",
        "terror man",
        "revival man",
        "the gamer",
        "girls of the wild",
        "kubera",
        "cheese in the trap",
        "shotgun boy",
        "see you in my 19th life",
        "solo max-level newbie",
        "max level newbie",
        "ranker's return",
        "worn and torn newbie",
        "skeleton soldier",
        "villain to kill",
        "breaker"
    )

    private val WEBTOON_WORD_REGEX = Regex(
        "\\b(webtoon|webtoons|manhwa|manhua|long strip|longstrip|vertical scroll|kakaopage|tapas|asura|reaper|flamescan|voidscan|lucaz)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isWebtoonMetadata(
        genres: List<String>? = null,
        tags: List<String>? = null,
        publishers: List<String>? = null,
        summary: String? = null,
        language: String? = null,
        seriesName: String? = null
    ): Boolean {
        // 1. Check Genres (Strict keywords)
        if (genres != null && genres.any { g -> WEBTOON_KEYWORDS.any { kw -> g.trim().equals(kw, ignoreCase = true) || g.contains(kw, ignoreCase = true) } }) {
            return true
        }

        // 2. Check Tags (Strict keywords)
        if (tags != null && tags.any { t -> WEBTOON_KEYWORDS.any { kw -> t.trim().equals(kw, ignoreCase = true) || t.contains(kw, ignoreCase = true) } }) {
            return true
        }

        // 3. Check Dedicated Webtoon Publishers / Studios / Scanlators
        if (publishers != null && publishers.any { p -> WEBTOON_PLATFORMS.any { pub -> p.contains(pub, ignoreCase = true) } }) {
            return true
        }

        // 4. Check Series Summary with word boundaries to avoid false matches
        if (!summary.isNullOrBlank()) {
            if (WEBTOON_WORD_REGEX.containsMatchIn(summary)) {
                return true
            }
        }

        // 5. Check Series Name / File Title
        if (!seriesName.isNullOrBlank()) {
            val lowerName = seriesName.lowercase()
                .replace('_', ' ')
                .replace('-', ' ')
                .replace(Regex("\\s+"), " ")

            if (KNOWN_WEBTOON_TITLES.any { title -> lowerName.contains(title) }) {
                return true
            }
            if (WEBTOON_WORD_REGEX.containsMatchIn(lowerName)) {
                return true
            }
            if (lowerName.contains("[webtoon]") || lowerName.contains("(webtoon)") ||
                lowerName.contains("[manhwa]") || lowerName.contains("(manhwa)") ||
                lowerName.contains("[manhua]") || lowerName.contains("(manhua)") ||
                lowerName.contains("[long strip]") || lowerName.contains("(long strip)")
            ) {
                return true
            }
        }

        return false
    }

    fun isWebtoonMetadata(metadata: SeriesMetadataDto?, seriesName: String? = null): Boolean {
        if (metadata == null && seriesName == null) return false
        val genres = metadata?.genres?.mapNotNull { it.title }
        val tags = metadata?.tags?.mapNotNull { it.title }
        val publishers = (metadata?.publishers.orEmpty() + metadata?.imprints.orEmpty()).mapNotNull { it.name }
        return isWebtoonMetadata(
            genres = genres,
            tags = tags,
            publishers = publishers,
            summary = metadata?.summary,
            language = metadata?.language,
            seriesName = seriesName
        )
    }

    fun isWebtoonDimensions(pageDimensions: Map<Int, FileDimensionDto>?): Boolean {
        if (pageDimensions.isNullOrEmpty()) return false
        val validDims = pageDimensions.values.filter { (it.width ?: 0) > 0 && (it.height ?: 0) > 0 }
        if (validDims.isEmpty()) return false

        var extremeTallSliceCount = 0
        var totalAspect = 0f
        var minAspect = Float.MAX_VALUE
        var maxAspect = Float.MIN_VALUE

        for (dim in validDims) {
            val aspect = dim.width!!.toFloat() / dim.height!!.toFloat()
            totalAspect += aspect
            if (aspect < minAspect) minAspect = aspect
            if (aspect > maxAspect) maxAspect = aspect
            // Slices narrower than 0.48 are definitely webtoon continuous strips
            if (aspect <= 0.48f) {
                extremeTallSliceCount++
            }
        }

        val avgAspect = totalAspect / validDims.size

        // 1. Definite extreme vertical strip slices (aspect <= 0.48)
        if (extremeTallSliceCount >= 1) return true

        // 2. High variance in slice heights with tall segments (spliced webtoon panels)
        // Standard comics (0.64-0.66) and manga (0.70-0.72) have uniform page dimensions.
        if ((maxAspect - minAspect) > 0.25f && minAspect < 0.52f) return true

        // 3. Dense slice episodes: very high slice count (>= 35) with slim vertical slices (avgAspect < 0.58)
        if (validDims.size >= 35 && avgAspect < 0.58f) return true

        return false
    }

    fun resolveEffectiveReadingDirection(
        preferredDirection: ReaderReadingDirection,
        autoWebtoonMode: Boolean,
        pageDimensions: Map<Int, FileDimensionDto>?,
        genres: List<String>? = null,
        tags: List<String>? = null,
        publishers: List<String>? = null,
        summary: String? = null,
        language: String? = null,
        seriesName: String? = null,
        seriesMetadata: SeriesMetadataDto? = null
    ): ReaderReadingDirection {
        // If explicitly set to Webtoon or Vertical, respect user choice
        if (preferredDirection == ReaderReadingDirection.Webtoon) {
            return ReaderReadingDirection.Webtoon
        }
        if (preferredDirection == ReaderReadingDirection.Vertical) {
            return ReaderReadingDirection.Vertical
        }

        // If Auto Webtoon mode is on, check if content is webtoon
        if (autoWebtoonMode) {
            val metadataMatch = isWebtoonMetadata(
                genres = genres ?: seriesMetadata?.genres?.mapNotNull { it.title },
                tags = tags ?: seriesMetadata?.tags?.mapNotNull { it.title },
                publishers = publishers ?: (seriesMetadata?.publishers.orEmpty() + seriesMetadata?.imprints.orEmpty()).mapNotNull { it.name },
                summary = summary ?: seriesMetadata?.summary,
                language = language ?: seriesMetadata?.language,
                seriesName = seriesName
            )
            if (metadataMatch || isWebtoonDimensions(pageDimensions)) {
                return ReaderReadingDirection.Webtoon
            }
        }

        return preferredDirection
    }
}

