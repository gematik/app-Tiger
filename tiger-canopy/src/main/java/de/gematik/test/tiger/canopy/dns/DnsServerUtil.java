/*
 *
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
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */
package de.gematik.test.tiger.canopy.dns;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

/** Shared utility methods for DNS server lifecycle management. */
class DnsServerUtil {

  private DnsServerUtil() {}

  static void closeQuietly(DatagramSocket s) {
    if (s != null && !s.isClosed()) {
      s.close();
    }
  }

  static void closeQuietly(ServerSocket s) {
    if (s == null) {
      return;
    }
    try {
      s.close();
    } catch (IOException e) {
      // already best-effort
    }
  }

  static void closeQuietly(Socket s) {
    if (s == null) {
      return;
    }
    try {
      s.close();
    } catch (IOException ignored) {
      // already best-effort
    }
  }

  static void joinQuietly(Thread t) {
    if (t == null) {
      return;
    }
    try {
      t.join(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
