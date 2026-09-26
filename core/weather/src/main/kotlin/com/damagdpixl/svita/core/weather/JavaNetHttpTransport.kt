package com.damagdpixl.svita.core.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/**
 * The single real-network implementation in the module, behind [HttpTransport].
 *
 * Chosen over Ktor deliberately (the "lightest that works" requirement):
 * `java.net.HttpURLConnection` ships with both the plain JVM (unit-test
 * toolchain) and Android API 26+ (this repo's minSdk), so the transport adds
 * zero dependencies and no multiplatform engine stack, while keeping the
 * injected seam the tests can fake. This class is never exercised by the
 * test suite — all tests run against fakes per the task constraints.
 */
public class JavaNetHttpTransport(
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) : HttpTransport {

    public override suspend fun get(url: String): HttpTransportResponse = withContext(Dispatchers.IO) {
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = true
            val status = connection.responseCode
            val stream = if (status in HTTP_OK_MIN..HTTP_OK_MAX) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            HttpTransportResponse(statusCode = status, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 10_000
        const val HTTP_OK_MIN = 200
        const val HTTP_OK_MAX = 299
    }
}
