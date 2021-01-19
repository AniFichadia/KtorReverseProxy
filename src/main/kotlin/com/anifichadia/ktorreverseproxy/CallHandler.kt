package com.anifichadia.ktorreverseproxy

import io.ktor.application.ApplicationCall
import io.ktor.client.HttpClient
import io.ktor.util.pipeline.PipelineContext

interface CallHandler {
    suspend fun handle(pipelineContext: PipelineContext<Unit, ApplicationCall>, client: HttpClient)
}