package com.anifichadia.ktorreverseproxy

import io.ktor.application.ApplicationCall
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.log
import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Companion
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent.ReadChannelContent
import io.ktor.http.content.OutgoingContent.WriteChannelContent
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.util.filter
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose

/** Lower-cased headers that should not be proxied to the mappings or client */
private val excludedProxiedHeaders = listOf(
    HttpHeaders.ContentType,
    HttpHeaders.ContentLength,
    HttpHeaders.TransferEncoding,
).map { it.toLowerCase() }

suspend fun PipelineContext<Unit, ApplicationCall>.proxyRequest(client: HttpClient, redirectTo: String) {
    val startTime = System.currentTimeMillis()

    val requestUri = call.request.uri
    val requestMethod = call.request.httpMethod
    val requestMethodValue = requestMethod.value
    val requestHeaders = call.request.headers
    val completeProxiedUrl = "$redirectTo$requestUri"
    log.debug("$requestUri ($requestMethodValue) -> $completeProxiedUrl")


    val proxiedResponse: HttpResponse = try {
        val res = client.request<HttpResponse>(completeProxiedUrl) {
            // Proxy the original request method to mapping to the mapped endpoint
            method = requestMethod

            // Proxy the original request headers (minus the excluded) to the mapped endpoint
            requestHeaders
                .filter { key, _ -> key.toLowerCase() !in excludedProxiedHeaders }
                .forEach { key, value -> header(key, value.joinToString()) }

            // Proxy the original request body to mapping to the mapped endpoint
            body = object : ReadChannelContent() {
                override val contentType: ContentType? = requestHeaders[HttpHeaders.ContentType]?.let(ContentType.Companion::parse)

                override fun readFrom(): ByteReadChannel = call.request.receiveChannel()
            }
        }
        res
    } catch (e: ResponseException) {
        e.response
    } catch (e: Throwable) {
        log.error("--> $requestUri ($requestMethodValue)", e)
        throw e
    }
    val proxyTime = System.currentTimeMillis()

    val proxiedResponseHeaders = proxiedResponse.headers
    call.respond(object : WriteChannelContent() {
        override val contentLength: Long? = proxiedResponseHeaders[HttpHeaders.ContentLength]?.toLong()
        override val contentType: ContentType? = proxiedResponseHeaders[HttpHeaders.ContentType]?.let(Companion::parse)
        override val headers: Headers = Headers.build {
            appendAll(
                proxiedResponseHeaders.filter { key, _ -> key.toLowerCase() !in excludedProxiedHeaders }
            )
        }
        override val status: HttpStatusCode = proxiedResponse.status
        override suspend fun writeTo(channel: ByteWriteChannel) {
            proxiedResponse.content.copyAndClose(channel)
        }
    })
    val endTime = System.currentTimeMillis()
    val roundTripTime = endTime - startTime

    log.debug("--> $requestUri ($requestMethodValue) -> $completeProxiedUrl (status: ${proxiedResponse.status}) (time: ${roundTripTime}ms (${proxyTime - startTime}ms - ${endTime - proxyTime}ms))")
}


val PipelineContext<*, ApplicationCall>.log
    get() = application.log


fun ByteReadChannel.hasContents() = availableForRead > 0
