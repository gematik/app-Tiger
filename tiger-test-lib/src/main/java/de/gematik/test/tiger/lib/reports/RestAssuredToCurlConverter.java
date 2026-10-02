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
package de.gematik.test.tiger.lib.reports;

import io.restassured.http.Header;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.MultiPartSpecification;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RestAssuredToCurlConverter {

  public static String toCurl(FilterableRequestSpecification requestSpec) {
    if (requestSpec == null) {
      return "";
    }

    StringBuilder curlCmd = new StringBuilder("curl -v");
    appendHeaders(curlCmd, requestSpec);
    appendCookies(curlCmd, requestSpec);
    appendFormParams(curlCmd, requestSpec);
    appendBody(curlCmd, requestSpec);
    appendMultipartParams(curlCmd, requestSpec);
    appendMethod(curlCmd, requestSpec);
    appendUri(curlCmd, requestSpec);

    return curlCmd.toString().trim() + " ";
  }

  private static void appendHeaders(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    if (requestSpec.getHeaders() != null) {
      for (Header header : requestSpec.getHeaders()) {
        curlCmd
            .append(" -H \"")
            .append(header.getName())
            .append(": ")
            .append(sanitizeHeaderValue(header.getValue()))
            .append("\"");
      }
    }
  }

  private static String sanitizeHeaderValue(String value) {
    return value == null ? "" : value.replace("\r", "").replace("\n", "");
  }

  private static void appendCookies(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    if (requestSpec.getCookies() != null && requestSpec.getCookies().exist()) {
      boolean hasCookieHeader =
          requestSpec.getHeaders() != null && requestSpec.getHeaders().hasHeaderWithName("Cookie");
      if (!hasCookieHeader) {
        StringBuilder cookieBuilder = new StringBuilder();
        for (io.restassured.http.Cookie cookie : requestSpec.getCookies()) {
          if (!cookieBuilder.isEmpty()) {
            cookieBuilder.append("; ");
          }
          cookieBuilder.append(cookie.getName()).append("=").append(cookie.getValue());
        }
        curlCmd.append(" -b \"").append(cookieBuilder).append("\"");
      }
    }
  }

  private static void appendFormParams(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    if (requestSpec.getFormParams() != null && !requestSpec.getFormParams().isEmpty()) {
      StringBuilder paramsStr = new StringBuilder();
      boolean first = true;
      for (Map.Entry<String, ?> entry : requestSpec.getFormParams().entrySet()) {
        if (!first) {
          paramsStr.append("&");
        } else {
          first = false;
        }
        paramsStr
            .append(entry.getKey())
            .append("=")
            .append(entry.getValue() != null ? entry.getValue() : "");
      }
      curlCmd.append(" --data \"").append(paramsStr).append("\"");
    }
  }

  private static void appendBody(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    Object body = requestSpec.getBody();
    if (body != null) {
      String bodyStr = bodyToString(body);
      if (!bodyStr.isEmpty()) {
        curlCmd.append(" -d '").append(bodyStr).append("'");
      }
    }
  }

  private static String bodyToString(Object body) {
    return body instanceof byte[] bytes
        ? new String(bytes, StandardCharsets.UTF_8)
        : body.toString();
  }

  private static void appendMultipartParams(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    if (requestSpec.getMultiPartParams() != null && !requestSpec.getMultiPartParams().isEmpty()) {
      for (MultiPartSpecification mp : requestSpec.getMultiPartParams()) {
        curlCmd
            .append(" -F \"")
            .append(mp.getControlName())
            .append("=")
            .append(mp.getContent())
            .append("\"");
      }
    }
  }

  private static void appendMethod(
      StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    String method = requestSpec.getMethod();
    if (StringUtils.isNotBlank(method)) {
      curlCmd.append(" -X ").append(method.toUpperCase());
    }
  }

  private static void appendUri(StringBuilder curlCmd, FilterableRequestSpecification requestSpec) {
    String uri = requestSpec.getURI();
    if (StringUtils.isNotBlank(uri)) {
      curlCmd.append(" \"").append(uri).append("\"");
    }
  }
}
