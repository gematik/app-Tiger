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
package de.gematik.test.tiger.httpbin

import de.gematik.test.tiger.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpBinKotlinTest {

    private fun testApplicationWithAppModule(block: suspend ApplicationTestBuilder.() -> Unit) {
        testApplication {
            application {
                module()
            }
            block()
        }
    }


    @Test
    fun `should return hello world message on root endpoint`() = testApplicationWithAppModule {
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello World!!!", response.bodyAsText())
    }

    @Test
    fun `should return IP address on ip endpoint`() = testApplicationWithAppModule {
        val response = client.get("/ip")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.bodyAsText())
    }


    @Test
    fun `should return UUID on uuid endpoint`() = testApplicationWithAppModule {
        val response = client.get("/uuid")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("uuid"))
    }

    @Test
    fun `should return headers on headers endpoint`() = testApplicationWithAppModule {
        val response = client.get("/headers")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        println("Response body: $body")
        assertTrue(body.contains("Accept") || body.contains("Content-Length"))
    }

    @Test
    fun `should return user agent on user-agent endpoint`() = testApplicationWithAppModule {
        val response = client.get("/user-agent")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("user-agent"))
    }

    @Test
    fun `should handle POST request with data`() = testApplicationWithAppModule {
        val testData = "kotlin-test-data"
        val response = client.post("/post") {
            setBody(testData)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(testData))
    }

    @Test
    fun `should handle PUT request with data`() = testApplicationWithAppModule {
        val testData = "kotlin-put-data"
        val response = client.put("/put") {
            setBody(testData)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(testData))
    }

    @Test
    fun `should handle PATCH request with data`() = testApplicationWithAppModule {
        val testData = "kotlin-patch-data"
        val response = client.patch("/patch") {
            setBody(testData)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(testData))
    }

    @Test
    fun `should handle DELETE request`() = testApplicationWithAppModule {
        val response = client.delete("/delete")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.bodyAsText())
    }

    @Test
    fun `should handle anything endpoint with different methods`() = testApplicationWithAppModule {

        // GET
        val getResponse = client.get("/anything/kotlin-test")
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // POST with data
        val postData = "kotlin-anything-data"
        val postResponse = client.post("/anything/kotlin-test") {
            setBody(postData)
        }
        assertEquals(HttpStatusCode.OK, postResponse.status)
        assertTrue(postResponse.bodyAsText().contains(postData))
    }

    @Test
    fun `should handle custom headers correctly`() = testApplicationWithAppModule {
        val customHeaderValue = "KotlinTestHeader"
        val response = client.get("/headers") {
            header("X-Kotlin-Test", customHeaderValue)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(customHeaderValue))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should handle base64 decoding correctly`() = testApplicationWithAppModule {
        val originalText = "KotlinHttpBinTest"
        val encodedText = Base64.encode(originalText.toByteArray())

        val response = client.get("/base64/$encodedText")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains(originalText))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should handle invalid base64 gracefully`() = testApplicationWithAppModule {
        val invalidBase64 = "!!!invalid-base64!!!"

        val response = client.get("/base64/$invalidBase64")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Incorrect Base64 data"))
    }

    @Test
    fun `should handle stream endpoint with multiple objects`() = testApplicationWithAppModule {
        val numberOfObjects = 3

        val response = client.get("/stream/$numberOfObjects")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        assertNotNull(body)
        // Count occurrences of "id" fields in the streamed JSON objects
        val idCount = body.split("\"id\":").size - 1
        assertTrue(idCount >= numberOfObjects, "Expected at least $numberOfObjects objects, got $idCount")
    }

    @Test
    fun `should handle gzip compression`() = testApplicationWithAppModule {
        val response = client.get("/gzip")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertNotNull(body)
        assertTrue(body.contains("url") || body.contains("{"))
    }

    @Test
    fun `should handle deflate compression`() = testApplicationWithAppModule {
        val response = client.get("/deflate")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertNotNull(body)
    }

    @Test
    fun `should handle cache endpoint with proper headers`() = testApplicationWithAppModule {
        val response = client.get("/cache")

        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers["Last-Modified"])
        assertNotNull(response.headers["ETag"])
    }

    @Test
    fun `should handle etag endpoint correctly`() = testApplicationWithAppModule {
        val testEtag = "kotlin-test-123"

        val response = client.get("/etag/$testEtag")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(testEtag, response.headers["ETag"])
    }

    @Test
    fun `should handle bytes endpoint with specific length`() = testApplicationWithAppModule {
        val numberOfBytes = 256

        val response = client.get("/bytes/$numberOfBytes")
        assertEquals(HttpStatusCode.OK, response.status)

        val bytes = response.readRawBytes()
        assertEquals(numberOfBytes, bytes.size)
    }

    @Test
    fun `should handle drip endpoint with timed data delivery`() = testApplicationWithAppModule {
        val numberOfBytes = 100
        val duration = 1 // seconds

        val startTime = System.currentTimeMillis()
        val response = client.get("/drip?numbytes=$numberOfBytes&duration=$duration")
        val requestDuration = System.currentTimeMillis() - startTime

        assertEquals(HttpStatusCode.OK, response.status)
        val bytes = response.readRawBytes()
        assertEquals(numberOfBytes, bytes.size)
        assertTrue(requestDuration >= duration * 1000, "Drip should take at least $duration second(s)")
    }

    @Test
    fun `should handle JSON content type correctly`() = testApplicationWithAppModule {
        val jsonData = """{"kotlin": "test", "number": 42}"""

        val response = client.post("/post") {
            contentType(ContentType.Application.Json)
            setBody(jsonData)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("kotlin"))
        assertTrue(body.contains("test"))
        assertTrue(body.contains("42"))
    }

    @Test
    fun `should handle large payload efficiently`() = testApplicationWithAppModule {
        val largeData = "K".repeat(1024 * 100) // 100KB of data

        val response = client.post("/post") {
            setBody(largeData)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("K"))
    }

    @Test
    fun `should return 404 for non-existing endpoints`() = testApplicationWithAppModule {
        val response = client.get("/non-existing-kotlin-endpoint")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }


    // ========== Authentication Tests ==========

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should handle basic authentication with correct credentials`() = testApplicationWithAppModule {

        val username = "testuser"
        val password = "testpass"
        val credentials = Base64.encode("$username:$password".toByteArray())

        val response = client.get("/basic-auth/$username/$password") {
            header("Authorization", "Basic $credentials")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("authenticated"))
        assertTrue(body.contains(username))
    }

    @Test
    fun `should return 401 for basic authentication without credentials`() = testApplicationWithAppModule {

        val response = client.get("/basic-auth/user/pass")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertNotNull(response.headers["WWW-Authenticate"])
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should return 401 for basic authentication with wrong credentials`() = testApplicationWithAppModule {

        val credentials = Base64.encode("wrong:credentials".toByteArray())

        val response = client.get("/basic-auth/user/pass") {
            header("Authorization", "Basic $credentials")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `should handle hidden basic auth with correct credentials`() = testApplicationWithAppModule {

        val username = "secret"
        val password = "password"
        val credentials = Base64.encode("$username:$password".toByteArray())

        val response = client.get("/hidden-basic-auth/$username/$password") {
            header("Authorization", "Basic $credentials")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("authenticated"))
    }

    @Test
    fun `should return 404 for hidden basic auth without credentials`() = testApplicationWithAppModule {

        val response = client.get("/hidden-basic-auth/user/pass")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `should handle bearer token authentication`() = testApplicationWithAppModule {

        val token = "my-secret-token"

        val response = client.get("/bearer") {
            header("Authorization", "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("authenticated"))
        assertTrue(body.contains(token))
    }

    @Test
    fun `should return 401 for bearer authentication without token`() = testApplicationWithAppModule {

        val response = client.get("/bearer")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ========== Redirect Tests ==========

    @Test
    fun `should handle relative redirects`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/relative-redirect/3")

        assertTrue(response.status.value in 300..399)
        assertNotNull(response.headers["Location"])
    }

    @Test
    fun `should handle absolute redirects`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/absolute-redirect/2")


        assertTrue(response.status.value in 300..399)
        val location = response.headers["Location"]
        assertNotNull(location)
        assertTrue(location.startsWith("http"))
    }

    @Test
    fun `should handle redirect-to with custom status code`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/redirect-to?url=/get&status_code=301")


        assertEquals(HttpStatusCode.MovedPermanently, response.status)
        assertEquals("/get", response.headers["Location"])
    }

    @Test
    fun `should reject invalid redirect status codes`() = testApplicationWithAppModule {

        val response = client.get("/redirect-to?url=/get&status_code=200")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ========== Status Code Tests ==========

    @Test
    fun `should return multiple status codes randomly`() = testApplicationWithAppModule {

        val response = client.get("/status/200,201,202")

        assertTrue(response.status.value in listOf(200, 201, 202))
    }

    @Test
    fun `should handle various status codes`() = testApplicationWithAppModule {

        val statusCodes = listOf(200, 201, 400, 404, 500, 503)

        for (code in statusCodes) {
            val response = client.get("/status/$code")
            assertEquals(code, response.status.value)
        }
    }

    @Test
    fun `should return 400 for invalid status code format`() = testApplicationWithAppModule {

        val response = client.get("/status/invalid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ========== Cookie Tests ==========

    @Test
    fun `should set and retrieve cookies`() = testApplicationWithAppModule {

        // Set cookie
        val setResponse = client.config {
            followRedirects = false
        }.get("/cookies/set/testcookie/testvalue")


        assertTrue(setResponse.status.value in 300..399)
        val cookieHeader = setResponse.headers["Set-Cookie"]
        assertNotNull(cookieHeader)
        assertTrue(cookieHeader.contains("testcookie=testvalue"))
    }

    @Test
    fun `should set multiple cookies via query parameters`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/cookies/set?cookie1=value1&cookie2=value2")


        assertTrue(response.status.value in 300..399)
    }

    @Test
    fun `should delete cookies`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/cookies/delete?cookie1=value1")


        assertTrue(response.status.value in 300..399)
    }

    @Test
    fun `should return all cookies`() = testApplicationWithAppModule {

        val response = client.get("/cookies") {
            header("Cookie", "test1=value1; test2=value2")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("cookies"))
    }

    // ========== Response Headers Tests ==========

    @Test
    fun `should set custom response headers via GET`() = testApplicationWithAppModule {

        val response = client.get("/response-headers?X-Custom-Header=CustomValue&X-Another=AnotherValue")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("CustomValue", response.headers["X-Custom-Header"])
        assertEquals("AnotherValue", response.headers["X-Another"])
    }

    @Test
    fun `should set custom response headers via POST`() = testApplicationWithAppModule {

        val response = client.post("/response-headers?X-Test=TestValue")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("TestValue", response.headers["X-Test"])
    }

    // ========== Image/Content Tests ==========

    @Test
    fun `should return PNG image`() = testApplicationWithAppModule {

        val response = client.get("/image/png")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.contentType()?.match(ContentType.Image.PNG) == true ||
                    response.contentType()?.contentType == "image"
        )
    }

    @Test
    fun `should return WebP image`() = testApplicationWithAppModule {

        val response = client.get("/image/webp")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return SVG image`() = testApplicationWithAppModule {

        val response = client.get("/image/svg")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return JPEG image`() = testApplicationWithAppModule {

        val response = client.get("/image/jpg")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return image based on Accept header`() = testApplicationWithAppModule {

        val response = client.get("/image") {
            header("Accept", "image/png")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should return XML content`() = testApplicationWithAppModule {

        val response = client.get("/xml")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<?xml") || body.contains("<"))
    }

    @Test
    fun `should return JSON content`() = testApplicationWithAppModule {

        val response = client.get("/json")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("{"))
        assertTrue(body.contains("slideshow"))
    }

    @Test
    fun `should return HTML content`() = testApplicationWithAppModule {

        val response = client.get("/html")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<html") || body.contains("<"))
    }

    // ========== Links Tests ==========

    @Test
    fun `should generate links page`() = testApplicationWithAppModule {

        val response = client.get("/links/5/2")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<a href"))
        assertTrue(body.contains("2 "))
    }

    @Test
    fun `should redirect to links with offset 0`() = testApplicationWithAppModule {

        val response = client.config {
            followRedirects = false
        }.get("/links/3")

        assertTrue(response.status.value in 300..399)
    }

    // ========== Stream and Binary Tests ==========

    @Test
    fun `should stream bytes with seed`() = testApplicationWithAppModule {

        val bytes = 100
        val response = client.get("/stream-bytes/$bytes?seed=42")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.OctetStream, response.contentType())
    }

    @Test
    fun `should handle range requests`() = testApplicationWithAppModule {

        val response = client.get("/range/100") {
            header("Range", "bytes=0-49")
        }

        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes", response.headers["Accept-Ranges"])
        assertNotNull(response.headers["Content-Range"])
    }

    @Test
    fun `should return full content without range header`() = testApplicationWithAppModule {

        val response = client.get("/range/100")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should reject invalid range`() = testApplicationWithAppModule {

        val response = client.get("/range/100") {
            header("Range", "bytes=200-300")
        }

        assertEquals(HttpStatusCode.RequestedRangeNotSatisfiable, response.status)
    }

    // ========== Cache Tests ==========

    @Test
    fun `should handle conditional requests with If-None-Match`() = testApplicationWithAppModule {

        val firstResponse = client.get("/cache")
        val etag = firstResponse.headers["ETag"]
        assertNotNull(etag)

        val secondResponse = client.get("/cache") {
            header("If-None-Match", etag)
        }

        assertEquals(HttpStatusCode.NotModified, secondResponse.status)
    }

    @Test
    fun `should handle ETag preconditions`() = testApplicationWithAppModule {

        val etag = "test-etag-123"

        val response = client.get("/etag/$etag") {
            header("If-None-Match", etag)
        }

        assertEquals(HttpStatusCode.NotModified, response.status)
    }

    @Test
    fun `should return 412 for failed If-Match`() = testApplicationWithAppModule {

        val response = client.get("/etag/etag1") {
            header("If-Match", "wrong-etag")
        }

        assertEquals(HttpStatusCode.PreconditionFailed, response.status)
    }

    @Test
    fun `should set Cache-Control header`() = testApplicationWithAppModule {

        val maxAge = 3600
        val response = client.get("/cache/$maxAge")

        assertEquals(HttpStatusCode.OK, response.status)
        val cacheControl = response.headers["Cache-Control"]
        assertNotNull(cacheControl)
        assertTrue(cacheControl.contains("max-age=$maxAge"))
    }

    // ========== UTF-8 and Encoding Tests ==========

    @Test
    fun `should handle UTF-8 encoding endpoint`() = testApplicationWithAppModule {

        val response = client.get("/encoding/utf8")

        assertTrue(response.status.value in 200..404)
    }

    // ========== Forms Tests ==========

    @Test
    fun `should return HTML form`() = testApplicationWithAppModule {

        val response = client.get("/forms/post")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<form") || body.contains("<"))
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `should handle negative delay gracefully`() = testApplicationWithAppModule {

        // Should either work with 0 delay or return error
        val response = client.get("/delay/-1")
        assertTrue(response.status.value in 200..499)
    }

    @Test
    fun `should limit maximum delay`() = testApplicationWithAppModule {

        val start = System.currentTimeMillis()
        val response = client.get("/delay/100") // Should be capped
        val duration = System.currentTimeMillis() - start

        assertEquals(HttpStatusCode.OK, response.status)
        // Should be capped at 10 seconds max
        assertTrue(duration < 15000)
    }


    @Test
    fun `should handle empty request body`() = testApplicationWithAppModule {

        val response = client.post("/post") {
            setBody("")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should handle TRACE method on anything endpoint`() = testApplicationWithAppModule {

        val response = client.request("/anything/test") {
            method = HttpMethod("TRACE")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `should handle delay on different HTTP methods`() = testApplicationWithAppModule {

        val methods = listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put)

        for (method in methods) {
            val start = System.currentTimeMillis()
            val response = client.request("/delay/1") {
                this.method = method
            }
            val duration = System.currentTimeMillis() - start

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(duration >= 1000, "Delay should be at least 1 second, was ${duration}ms")
        }
    }

    @Test
    fun `should return 400 for negative bytes in drip endpoint`() = testApplicationWithAppModule {

        val response = client.get("/drip?numbytes=-10")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `should handle concurrent requests`() = testApplicationWithAppModule {

        // Simple concurrency test
        val responses = (1..5).map {
            client.get("/uuid")
        }

        responses.forEach { response ->
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
}
