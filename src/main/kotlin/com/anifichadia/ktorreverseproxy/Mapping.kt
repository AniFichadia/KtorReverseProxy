package com.anifichadia.ktorreverseproxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.request.ApplicationRequest
import io.ktor.request.path
import io.ktor.util.pipeline.PipelineContext
import kotlin.text.RegexOption.IGNORE_CASE

abstract class Mapping(
    private val redirectTo: String,
) : CallHandler {

    abstract fun matches(request: ApplicationRequest): Boolean

    override suspend fun handle(pipelineContext: PipelineContext<Unit, ApplicationCall>, client: HttpClient) {
        pipelineContext.proxyRequest(client, redirectTo)
    }


    class SimpleMapping(
        redirectTo: String,
        private val matcher: String,
    ) : Mapping(redirectTo) {
        override fun matches(request: ApplicationRequest): Boolean = request.path().startsWith(matcher)
    }

    class RegexMapping(
        redirectTo: String,
        private val matcher: Regex,
    ) : Mapping(redirectTo) {
        constructor(redirectTo: String, matcherString: String) : this(redirectTo, matcherString.toRegex(IGNORE_CASE))

        override fun matches(request: ApplicationRequest): Boolean = request.path() matches matcher
    }
}