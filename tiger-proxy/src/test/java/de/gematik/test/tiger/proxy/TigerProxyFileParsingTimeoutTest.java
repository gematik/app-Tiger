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

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class TigerProxyFileParsingTimeoutTest {

  @Test
  void ensureFileIsParsedHonorsTheConfiguredFileParsingTimeout() throws Exception {
    try (TigerProxy proxy =
        new TigerProxy(TigerProxyConfiguration.builder().fileParsingTimeoutInSeconds(0).build())) {
      setFileParsingFuture(proxy, new CompletableFuture<>());

      assertThatThrownBy(proxy::ensureFileIsParsed)
          .as("tigerProxy.fileParsingTimeoutInSeconds has to reach ensureFileIsParsed")
          .isInstanceOf(TigerProxyStartupException.class)
          .hasMessageContaining("Gave up after 0 seconds");
    }
  }

  private void setFileParsingFuture(TigerProxy proxy, CompletableFuture<Void> future)
      throws Exception {
    Field field = AbstractTigerProxy.class.getDeclaredField("fileParsingFuture");
    field.setAccessible(true);
    field.set(proxy, future);
  }
}
