package com.example.stockflow.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import com.example.stockflow.models.BadRequestException
import com.example.stockflow.models.ConflictException
import com.example.stockflow.models.NotFoundException
import com.example.stockflow.models.UnauthorizedException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to cause.message))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("error" to cause.message))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to cause.message))
        }
        exception<UnauthorizedException> { call, cause ->
            val body = mutableMapOf("error" to (cause.message ?: "Unauthorized"))
            cause.code?.let { body["code"] = it }
            call.respond(HttpStatusCode.Unauthorized, body)
        }
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An unexpected error occurred"))
        }
    }
}
