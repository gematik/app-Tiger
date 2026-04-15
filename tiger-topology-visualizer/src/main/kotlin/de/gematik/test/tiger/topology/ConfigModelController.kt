/*
 * Copyright 2021-2026 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.topology

import de.gematik.test.tiger.topology.util.SafeToSendToClientException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Controller
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.multipart.MultipartFile

@Controller
@RequestMapping("/topology")

class ConfigModelController {

    private val logger = KotlinLogging.logger {}

    @PostMapping("/upload")
    @ResponseBody
    fun uploadFiles(@RequestParam("files") files: List<MultipartFile>): ConfigurationDiagramModel {
        val uploadedFiles = files.mapNotNull { multipartFile ->
            multipartFile.originalFilename
                ?.takeIf(::filterYamlFileName)
                ?.let {
                    UploadedYamlFile(
                        it,
                        multipartFile.inputStream.bufferedReader().use { reader -> reader.readText() })
                }
        }

        return convertUploadedFilesToDiagramModel(uploadedFiles)
    }

    @ExceptionHandler(SafeToSendToClientException::class)
    @ResponseBody
    fun handleSafeToSend(exception: SafeToSendToClientException): ProblemDetail {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            exception.message ?: "Bad request"
        ).apply {
            title = "Bad Request"
        }
    }

    @ExceptionHandler(Exception::class)
    @ResponseBody
    fun handleUnexpectedException(exception: Exception): ProblemDetail {
        logger.error(exception) { "Unexpected error while processing topology upload" }
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            exception.message ?: "An unexpected error occurred while processing the topology upload."
        ).apply {
            title = "Internal Server Error"
        }
    }
}

private fun filterYamlFileName(fileName: String): Boolean {
    return fileName.endsWith(".yaml", ignoreCase = true) || fileName.endsWith(".yml", ignoreCase = true)
}


