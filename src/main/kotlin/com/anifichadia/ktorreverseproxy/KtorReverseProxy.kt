package com.anifichadia.ktorreverseproxy

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.features.*
import io.ktor.request.*
import org.slf4j.event.Level
import java.util.concurrent.TimeUnit

/**
 * Based off: https://github.com/ktorio/ktor-samples/blob/1.3.0/other/reverse-proxy/src/ReverseProxyApplication.kt
 * Contains a few changes to forward the request body also
 */
class KtorReverseProxy(
    val fallback: Fallback,
    val mappings: List<Mapping> = emptyList(),
) {

    fun configure(application: Application) = application.apply {
        val client = HttpClient {
            // Took me a good couple of hours to figure out why error responses weren't being forwarded. HttpClient's
            // default `expectSuccess` to true. When it is true, the HttpClient pipeline response validation step throws
            // an exception instead of consuming the response. For a gateway, we're expecting potential error responses,
            // which we want to consume and forward to clients.
            expectSuccess = false

            install(HttpTimeout) {
                val timeoutMillis = TimeUnit.SECONDS.toMillis(60)
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
            install(CallLogging) {
                logger = log
                level = Level.TRACE
            }
        }
        environment.monitor.subscribe(ApplicationStopping) {
            client.close()
        }

        intercept(ApplicationCallPipeline.Call) {
            val requestUri = call.request.uri
            val requestMethodValue = call.request.httpMethod.value

            val hasBody = call.request.receiveChannel().hasContents()

            log.debug("<-- $requestUri ($requestMethodValue, body: $hasBody)")

            val mapping = mappings.find { it.matches(call.request) }
            val callHandler = if (mapping != null) {
                mapping
            } else {
                log.debug("$requestUri: Using fallback")
                fallback
            }
            callHandler.handle(this, client)
        }
    }
}