/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.cio.toByteArray
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.max
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


suspend fun RoutingCall.redirectToPath(
    path: String,
    absolutePath: Boolean = false,
) {
    val redirectionPath =
        if (absolutePath) {
            "${this.request.origin.scheme}://${this.request.host()}:${this.request.port()}$path"
        } else {
            path
        }
    this.respondRedirect(redirectionPath)
}

fun convertSecondsToMillis(seconds: Float): Long = (seconds * 1000).toLong()

suspend fun RoutingCall.respondWithResource(
    pathToResource: String,
    contentType: ContentType? = null
) {
    val resource = this.resolveResource(pathToResource);
    val contentType = contentType ?: ContentType.defaultForFilePath(pathToResource)
    if (resource != null) {
        this.respondBytes(resource.readFrom().toByteArray(), contentType)
    } else {
        this.respond(HttpStatusCode.NotFound, "Resource not found")
    }
}


@OptIn(ExperimentalUuidApi::class, ExperimentalEncodingApi::class)
fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!!!")
        }

        get("/html") {
            call.respondWithResource("/templates/moby.html")
        }

        get("/ip") {
            call.respond(mapOf("hello" to call.request.origin.remoteHost))
        }

        get("/uuid") {
            call.respond(mapOf("uuid" to Uuid.random()))
        }

        get("/headers") {
            call.respond(call.request.headers.toMap())
        }

        get("/user-agent") {
            call.respond(mapOf("user-agent" to call.request.headers["user-agent"]))
        }

        get("/get") {
            call.respond(
                createRequestInfo(call),
            )
        }

        route("/anything/{anything?}") {
            fun Route.respondWithInfoFromRequest() {
                handle {
                    call.respond(
                        createRequestInfoWithData(call),
                    )
                }
            }
            method(HttpMethod.Get) { respondWithInfoFromRequest() }
            method(HttpMethod.Post) { respondWithInfoFromRequest() }
            method(HttpMethod.Put) { respondWithInfoFromRequest() }
            method(HttpMethod.Delete) { respondWithInfoFromRequest() }
            method(HttpMethod.Patch) { respondWithInfoFromRequest() }
            method(HttpMethod("TRACE")) { respondWithInfoFromRequest() }
        }

        post("/post") {
            call.respond(
                createRequestInfoWithData(call),
            )
        }

        put("/put") {
            call.respond(
                createRequestInfoWithData(call),
            )
        }

        patch("/patch") {
            call.respond(
                createRequestInfoWithData(call),
            )
        }

        delete("/delete") {
            call.respond(
                createRequestInfoWithData(call),
            )
        }

        get("/gzip") {
            val responseData = createRequestInfo(call)

            val byteArrayOutputStream = ByteArrayOutputStream()
            GZIPOutputStream(byteArrayOutputStream).use { gzipOutputStream ->
                gzipOutputStream.write(responseData.toString().toByteArray())
            }

            val compressedData = byteArrayOutputStream.toByteArray()

            call.response.header(HttpHeaders.ContentEncoding, "gzip")
            call.respondBytes(compressedData, ContentType.Application.Json)
        }

        get("/deflate") {
            val responseData = createRequestInfo(call)

            val byteArrayOutputStream = ByteArrayOutputStream()
            DeflaterOutputStream(byteArrayOutputStream).use { deflaterOutputStream ->
                deflaterOutputStream.write(responseData.toString().toByteArray())
            }

            val compressedData = byteArrayOutputStream.toByteArray()

            call.response.header(HttpHeaders.ContentEncoding, "deflate")
            call.respondBytes(compressedData, ContentType.Application.Json)
        }

        get("/redirect/{n}") {
            val n = call.parameters["n"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val absolute = call.parameters["absolute"].toBoolean()

            if (n == 1) {
                call.respondRedirect("/get", absolute)
            } else {
                val redirectPath = if (absolute) "absolute-redirect" else "relative-redirect"
                call.redirectToPath("/$redirectPath/${n - 1}", absolute)
            }
        }

        route("/redirect-to") {
            fun Route.redirectTo() {
                handle {
                    val url = call.parameters["url"] ?: ""
                    val statusCode = call.parameters["status_code"]?.toIntOrNull() ?: 302
                    if (statusCode in 300..<400) {
                        call.response.headers.append(HttpHeaders.Location, url)
                        call.respond(HttpStatusCode.fromValue(statusCode))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Invalid status code")
                    }
                }
            }

            method(HttpMethod.Get) { redirectTo() }
            method(HttpMethod.Post) { redirectTo() }
            method(HttpMethod.Put) { redirectTo() }
            method(HttpMethod.Delete) { redirectTo() }
            method(HttpMethod.Patch) { redirectTo() }
            method(HttpMethod("TRACE")) { redirectTo() }
        }

        get("/relative-redirect/{n}") {
            val n = call.parameters["n"]?.toIntOrNull() ?: 1
            if (n == 1) {
                call.redirectToPath("/get")
            } else {
                call.redirectToPath("/relative-redirect/${n - 1}")
            }
        }

        get("/absolute-redirect/{n}") {
            val n = call.parameters["n"]?.toIntOrNull() ?: 1
            if (n == 1) {
                call.redirectToPath("/get", true)
            } else {
                call.redirectToPath("/absolute-redirect/${n - 1}", true)
            }
        }

        get("/stream/{n}") {
            val requestInfo = createRequestInfo(call)
            val n = call.parameters["n"]?.toIntOrNull()?.coerceAtMost(100) ?: 1
            call.respondTextWriter(ContentType.Application.Json) {
                for (i in 0..<n) {
                    val response = createResponseWithId(i, requestInfo)
                    write(Json.encodeToString(response))
                    flush()
                    delay(1000)
                }
            }
        }
        route("/status/{codes}") {
            fun isValidStatusCode(value: HttpStatusCode): Boolean = value.description != "Unknown Status Code"

            fun Route.randomStatusCode() {
                handle {
                    val codes = call.parameters["codes"]?.split(",") ?: emptyList()
                    if (codes.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid status code")
                    }
                    try {
                        var statusCodes = codes.map { it.toInt() }.map { HttpStatusCode.fromValue(it) }
                        var codeToReturn = statusCodes.random()
                        if (isValidStatusCode(codeToReturn)) {
                            call.respond(codeToReturn)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Invalid status code")
                        }
                    } catch (_: NumberFormatException) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid status code")
                    }
                }
            }
            method(HttpMethod.Get) { randomStatusCode() }
            method(HttpMethod.Post) { randomStatusCode() }
            method(HttpMethod.Put) { randomStatusCode() }
            method(HttpMethod.Delete) { randomStatusCode() }
            method(HttpMethod.Patch) { randomStatusCode() }
            method(HttpMethod("TRACE")) { randomStatusCode() }
        }

        route("/response-headers") {
            fun Route.setHeaders() {
                handle {
                    call.parameters.entries().forEach { (key, values) ->
                        values.forEach { value ->
                            call.response.headers.append(key, value)
                        }
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }
            method(HttpMethod.Get) { setHeaders() }
            method(HttpMethod.Post) { setHeaders() }
        }

        get("/cookies") {
            val cookies = call.request.cookies.rawCookies
            call.respond(mapOf("cookies" to cookies))
        }

        get("/forms/post") {
            call.respondWithResource("/templates/forms-post.html")
        }

        fun isSecureRequest(call: ApplicationCall): Boolean = call.request.origin.scheme == "https"

        get("/cookies/set/{name}/{value}") {
            val name = call.parameters["name"] ?: ""
            val value = call.parameters["value"] ?: ""
            val secure = isSecureRequest(call)

            //need to specify the path, otherwise the cookie is not visible in the /cookies path
            call.response.cookies.append(name, value, path = "/cookies", secure = secure)
            call.respondRedirect("/cookies")
        }

        get("/cookies/set") {
            val secure = isSecureRequest(call)
            call.request.queryParameters.entries().forEach { (key, values) ->
                values.forEach { value ->
                    call.response.cookies.append(key, value, secure = secure)
                }
            }
            call.respondRedirect("/cookies")
        }

        get("/cookies/delete") {
            call.request.queryParameters.entries().forEach { (key, values) ->
                values.forEach { value ->
                    call.response.cookies.append(key, value = "", maxAge = 0)
                }
            }
            call.respondRedirect("/cookies")
        }

        @Serializable
        data class AuthResponse(
            val authenticated: Boolean,
            val user: String,
        )

        get("/basic-auth/{user}/{passwd}") {
            val user = call.parameters["user"] ?: ""
            val passwd = call.parameters["passwd"] ?: ""

            val authorizationHeader = call.request.authorization()
            if (authorizationHeader.isNullOrEmpty() || !authorizationHeader.startsWith("Basic ")) {
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"Fake Realm\"")
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val base64Credentials = authorizationHeader.removePrefix("Basic ")
                val credentials = String(base64Credentials.decodeBase64Bytes()).split(":", limit = 2)
                if (credentials.size == 2 && credentials[0] == user && credentials[1] == passwd) {
                    call.respond(AuthResponse(true, user))
                } else {
                    call.response.headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"Fake Realm\"")
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }

        get("/hidden-basic-auth/{user}/{passwd}") {
            val user = call.parameters["user"] ?: ""
            val passwd = call.parameters["passwd"] ?: ""

            val authorizationHeader = call.request.authorization()
            if (authorizationHeader.isNullOrEmpty() || !authorizationHeader.startsWith("Basic ")) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                val base64Credentials = authorizationHeader.removePrefix("Basic ")
                val credentials = String(base64Credentials.decodeBase64Bytes()).split(":", limit = 2)
                if (credentials.size == 2 && credentials[0] == user && credentials[1] == passwd) {
                    call.respond(AuthResponse(true, user))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        get("/bearer") {
            val authorizationHeader = call.request.authorization()
            if (authorizationHeader.isNullOrEmpty() || !authorizationHeader.startsWith("Bearer ")) {
                call.response.headers.append(HttpHeaders.WWWAuthenticate, "Bearer")
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val token = authorizationHeader.removePrefix("Bearer ")
                call.respond(AuthResponse(true, token))
            }
        }

        route("/delay/{delay}") {
            fun Route.delaySeconds() {
                handle {
                    val delay = call.parameters["delay"]!!.toFloat().coerceAtMost(10f)
                    delay(convertSecondsToMillis(delay))
                    call.respond(createRequestInfo(call))
                }
            }
            method(HttpMethod.Get) { delaySeconds() }
            method(HttpMethod.Post) { delaySeconds() }
            method(HttpMethod.Put) { delaySeconds() }
            method(HttpMethod.Delete) { delaySeconds() }
            method(HttpMethod.Patch) { delaySeconds() }
            method(HttpMethod("TRACE")) { delaySeconds() }
        }

        get("/drip") {


            fun generateBytes(
                numBytes: Int,
                pause: Float,
            ) = flow {
                repeat(numBytes) {
                    emit("*".toByteArray())
                    delay(convertSecondsToMillis(pause))
                }
            }

            val duration = call.request.queryParameters["duration"]?.toFloatOrNull() ?: 2f
            val numbytes =
                call.request.queryParameters["numbytes"]
                    ?.toIntOrNull()
                    ?.coerceAtMost(10 * 1024 * 1024) ?: 10
            val statusCode = call.request.queryParameters["code"]?.toIntOrNull() ?: 200

            if (numbytes <= 0) {
                call.respond(HttpStatusCode.BadRequest, "number of bytes must be positive")
            }

            val delay = call.request.queryParameters["delay"]?.toFloatOrNull() ?: 0f
            if (delay > 0) {
                delay(convertSecondsToMillis(delay))
            }

            val pause = duration / numbytes

            val responseFLow = generateBytes(numbytes, pause)

            call.respondBytesWriter(status = HttpStatusCode.fromValue(statusCode)) {
                responseFLow.collect { byteArray ->
                    writeFully(byteArray)
                    flush()
                }
            }
        }

        get("/base64/{value?}") {
            val defaultValue = "SFRUUEJJTiBpcyBhd2Vzb21l"
            val value = call.parameters["value"] ?: defaultValue

            val response = try {
                Base64.UrlSafe.decode(value).decodeToString()
            } catch (_: IllegalArgumentException) {
                "Incorrect Base64 data try: SFRUUEJJTiBpcyBhd2Vzb21l"
            }

            call.respondText(
                response,
                ContentType.Text.Html
            )
        }

        get("/cache") {
            val isConditional =
                call.request.headers[HttpHeaders.IfModifiedSince] != null || call.request.headers[HttpHeaders.IfNoneMatch] != null

            if (isConditional) {
                call.respond(HttpStatusCode.NotModified)
            } else {
                call.response.headers.append(HttpHeaders.LastModified, GMTDate().toHttpDate())
                call.response.headers.append(HttpHeaders.ETag, Uuid.random().toHexString())
                val requestInfo = createRequestInfo(call)
                call.respond(requestInfo)
            }
        }

        get("/etag/{etag}") {
            val ifNoneMatch =
                call.request.headers[HttpHeaders.IfNoneMatch]?.split(",")?.map { it.trim() } ?: emptyList()
            val ifMatch = call.request.headers[HttpHeaders.IfMatch]?.split(",")?.map { it.trim() } ?: emptyList()
            val etag = call.parameters["etag"]!!

            if (ifNoneMatch.isNotEmpty()) {
                if (ifNoneMatch.contains(call.parameters["etag"]) || ifNoneMatch.contains("*")) {
                    call.response.headers.append(HttpHeaders.ETag, etag)
                    call.respond(HttpStatusCode.NotModified)
                }
            } else if (ifMatch.isNotEmpty() && !ifMatch.contains(etag) && !ifMatch.contains("*")) {
                call.respond(HttpStatusCode.PreconditionFailed)
            } else {
                val requestInfo = createRequestInfo(call)
                call.response.headers.append(HttpHeaders.ETag, etag)
                call.respond(requestInfo)
            }
        }

        get("/cache/{value}") {
            val value = call.parameters["value"]?.toIntOrNull() ?: 0
            val requestInfo = createRequestInfo(call)
            call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=$value")
            call.respond(requestInfo)
        }

        get("/encoding/utf8") {
            call.respondWithResource("templates/UTF-8-demo.txt", ContentType.Text.Html.withCharset(Charsets.UTF_8))
        }

        get("/bytes/{n}") {
            val n = call.parameters["n"]?.toIntOrNull()?.coerceAtMost(100 * 1024) ?: 0

            val seed = call.request.queryParameters["seed"]?.toIntOrNull()

            val random = if (seed != null) {
                Random(seed)
            } else {
                Random.Default
            }
            call.respondBytes(random.nextBytes(n), ContentType.Application.OctetStream)

        }

        get("/stream-bytes/{n}") {
            val n = call.parameters["n"]?.toIntOrNull()?.coerceAtMost(100 * 1024) ?: 0
            val seed = call.request.queryParameters["seed"]?.toIntOrNull()
            val chunkSize = call.request.queryParameters["chunk_size"]?.toIntOrNull() ?: (10 * 1024)

            val random = if (seed != null) {
                Random(seed)
            } else {
                Random.Default
            }

            call.respondBytesWriter(ContentType.Application.OctetStream) {
                for (i in 0..<n) {
                    writeByte(random.nextInt(256).toByte())
                    if (i % chunkSize == 0) {
                        flush()
                    }
                }
            }
        }

        get("/range/{numbytes}") {
            fun parseRequestRange(rangeHeaderText: String): Pair<Int?, Int?> {
                val ranges = rangeHeaderText.split(",")
                if (ranges.isNotEmpty()) {
                    val range = ranges[0].removePrefix("bytes=").split("-")
                    if (range.size == 2) {
                        return Pair(range[0].toIntOrNull(), range[1].toIntOrNull())
                    }
                }
                return Pair(null, null)
            }

            fun getRequestRange(
                rangeHeaderText: String,
                numbytes: Int,
            ): Pair<Int, Int> {
                var (startPos, endPos) = parseRequestRange(rangeHeaderText)
                when {
                    startPos == null && endPos == null -> {
                        //request full range
                        startPos = 0
                        endPos = numbytes - 1
                    }

                    startPos == null -> {
                        //request last x bytes
                        //if we are here then end is no longer null.
                        startPos = max(0, numbytes - endPos!!)
                    }

                    endPos == null -> {
                        //request last x bytes
                        endPos = numbytes - 1
                    }
                }
                return Pair(startPos, endPos)
            }

            val numbytes = call.parameters["numbytes"]?.toIntOrNull() ?: 0

            if (numbytes <= 0 || numbytes > (100 * 1024)) {
                call.response.headers.append(HttpHeaders.ETag, "range$numbytes")
                call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")

                call.respond(HttpStatusCode.NotFound, "number of bytes must be in range (0, 102400)")
                return@get
            }


            val chunkSize = call.request.queryParameters["chunk_size"]?.toIntOrNull() ?: (10 * 1024)
            val duration = call.request.queryParameters["duration"]?.toFloatOrNull() ?: 0f
            val pausePerByte = convertSecondsToMillis(duration / numbytes)


            val (start, end) = getRequestRange(call.request.headers[HttpHeaders.Range] ?: "", numbytes)
            val rangeLength = (end + 1) - start

            if (start > end || start !in 0..numbytes || end !in 0..numbytes) {
                call.response.headers.append(HttpHeaders.ETag, "range$numbytes")
                call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
                call.response.headers.append(HttpHeaders.ContentRange, "bytes */$numbytes")
                call.response.headers.append(HttpHeaders.ContentLength, "0")

                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                return@get
            }

            call.response.headers.append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            call.response.headers.append(HttpHeaders.ETag, "range$numbytes")
            call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
            call.response.headers.append(HttpHeaders.ContentLength, rangeLength.toString())
            call.response.headers.append(HttpHeaders.ContentRange, "bytes $start-$end/$numbytes")


            if (start == 0 && end == (numbytes - 1)) {
                call.response.status(HttpStatusCode.OK)
            } else {
                call.response.status(HttpStatusCode.PartialContent)
            }

            call.respondBytesWriter {
                for (i in start..<end + 1) {
                    writeByte(('a'.code + (i % 26)).toByte())
                    flush()
                    if (i % chunkSize == 0) {
                        delay(pausePerByte * chunkSize)
                    }
                }
            }
        }

        get("/links/{n}/{offset}") {
            val n = call.parameters["n"]?.toIntOrNull()?.coerceIn(1, 200) ?: 1
            val offset = call.parameters["offset"]?.toIntOrNull() ?: 0

            val link = "<a href='%s'>%s</a> "

            val html = mutableListOf("<html><head><title>Links</title></head><body>")

            for (i in 0..<n) {
                if (i == offset) {
                    html.add("$i ")
                } else {
                    html.add(link.format("/links/$n/$i", i))
                }
            }
            html.add("</body></html>")

            call.respondText(contentType = ContentType.Text.Html, text = html.joinToString(""))

        }

        get("/links/{n}") {
            val n = call.parameters["n"]?.toIntOrNull()?.coerceIn(1, 200) ?: 1
            call.respondRedirect("/links/$n/0")
        }

        get("/image") {

            val accept = call.request.headers[HttpHeaders.Accept]?.lowercase() ?: "image/png"


            when {
                accept.contains("image/webp") -> call.respondWithResource(
                    "/templates/images/wolf_1.webp"
                )

                accept.contains("image/svg+xml") -> call.respondWithResource("/templates/images/svg_logo.svg")
                accept.contains("image/jpeg") -> call.respondWithResource("/templates/images/jackal.jpg")
                accept.contains("image/png") || accept.contains("image/*") -> call.respondWithResource("/templates/images/pig_icon.png")
                else -> call.respond(HttpStatusCode.UnsupportedMediaType)
            }
        }

        get("/image/png") {
            call.respondWithResource("/templates/images/pig_icon.png")
        }


        get("image/webp") { call.respondWithResource("/templates/images/wolf_1.webp") }

        get("image/svg") { call.respondWithResource("/templates/images/svg_logo.svg") }

        get("image/jpg") { call.respondWithResource("/templates/images/jackal.jpg") }

        get("/xml") {
            call.respondWithResource("/templates/sample.xml")
        }

        get("/json") {
            call.respondText(
                """{
  "slideshow": {
    "title": "Sample Slide Show",
    "date": "date of publication",
    "author": "Yours Truly",
    "slides": [
      {
        "type": "all",
        "title": "Wake up to WonderWidgets!"
      },
      {
        "type": "all",
        "title": "Overview",
        "items": [
          "Why <em>WonderWidgets</em> are great",
          "Who <em>buys</em> WonderWidgets"
        ]
      }
    ]
  }
}""",
                ContentType.Application.Json
            )
        }
    }
}
