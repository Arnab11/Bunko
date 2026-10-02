package com.bunko.reader

import java.lang.reflect.Proxy

private fun noServer(method: String): Nothing {
    throw IllegalStateException("No server configured ($method)")
}

internal val UnconfiguredKavitaApi: KavitaApi =
    Proxy.newProxyInstance(
        KavitaApi::class.java.classLoader,
        arrayOf(KavitaApi::class.java)
    ) { _, method, _ -> noServer(method.name) } as KavitaApi

internal val UnconfiguredKomgaApi: KomgaApi =
    Proxy.newProxyInstance(
        KomgaApi::class.java.classLoader,
        arrayOf(KomgaApi::class.java)
    ) { _, method, _ -> noServer(method.name) } as KomgaApi
