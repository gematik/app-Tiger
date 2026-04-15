/*
 * Copyright 2026 gematik GmbH
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
package de.gematik.test.tiger.screenshots;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/** HTTP operations (GET/PUT/DELETE) against the Nexus raw repository. */
@Slf4j
class Store {

  private final String baseUrl;
  private final String authHeader;
  private final boolean dryRun;
  private final HttpClient http = HttpClient.newBuilder().build();

  Store(Config cfg) {
    this.baseUrl = cfg.storeUrl();
    this.dryRun = cfg.dryRun();
    this.authHeader =
        (cfg.storeUser() != null && cfg.storePassword() != null)
            ? "Basic "
                + Base64.getEncoder()
                    .encodeToString((cfg.storeUser() + ":" + cfg.storePassword()).getBytes())
            : null;
  }

  boolean download(String remotePath, Path localFile) {
    try {
      var resp =
          http.send(req(remotePath).GET().build(), HttpResponse.BodyHandlers.ofFile(localFile));
      return resp.statusCode() >= 200 && resp.statusCode() < 300;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  void upload(Path localFile, String remotePath) throws Exception {
    if (dryRun) {
      log.info("DRY_RUN: would upload → {}", remotePath);
      return;
    }
    http.send(
        req(remotePath).PUT(HttpRequest.BodyPublishers.ofFile(localFile)).build(),
        HttpResponse.BodyHandlers.discarding());
  }

  void delete(String remotePath) {
    if (dryRun) {
      log.info("DRY_RUN: would delete {}", remotePath);
      return;
    }
    try {
      http.send(req(remotePath).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception ignored) {
    }
  }

  private HttpRequest.Builder req(String path) {
    var b = HttpRequest.newBuilder(URI.create(baseUrl + "/" + path));
    if (authHeader != null) b.header("Authorization", authHeader);
    return b;
  }
}
