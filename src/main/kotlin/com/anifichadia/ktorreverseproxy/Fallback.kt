package com.anifichadia.ktorreverseproxy

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext

abstract class Fallback : CallHandler {

    class RespondWithHttpStatusCode(
        private val httpStatusCode: HttpStatusCode = HttpStatusCode.NotFound,
    ) : Fallback() {

        override suspend fun handle(pipelineContext: PipelineContext<Unit, ApplicationCall>, client: HttpClient) {
            pipelineContext.call.respond(httpStatusCode)
        }
    }

    class ToUrl(
        private val redirectTo: String,
    ) : Fallback() {

        override suspend fun handle(pipelineContext: PipelineContext<Unit, ApplicationCall>, client: HttpClient) {
            pipelineContext.proxyRequest(client, redirectTo)
        }
    }
}