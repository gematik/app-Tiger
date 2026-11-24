/*
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.common.jexl;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tiger.CustomerJexlMethods;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegisterAnnotatedJexlMethodsTest {

  @Test
  void indexContainsBuiltInAndCustomerTestNamespaces() {
    var indexed = TigerJexlExecutor.findIndexedNamespaces(List.of(getClass().getClassLoader()));
    assertThat(indexed)
        .containsEntry("test", DummyJexlMethods.class)
        .containsEntry("customer", CustomerJexlMethods.class);
  }

  /** Tests that the {@link DummyJexlMethods} are registered by calling them. */
  @Test
  void testRegisterAnnotatedJexlMethods() {
    var resultJexlExpression =
        TigerJexlExecutor.evaluateJexlExpression("test:testMethod()", new TigerJexlContext());
    assertThat(resultJexlExpression).hasValue("test");
    var resultResolvePlaceHolders =
        TigerGlobalConfiguration.resolvePlaceholders("!{test:testMethod()}");
    assertThat(resultResolvePlaceHolders).isEqualTo("test");
  }

  @Test
  void registersCustomerNamespaceBeforeConfigurationEvaluation() {
    assertThat(TigerGlobalConfiguration.resolvePlaceholders("!{customer:testMethod()}"))
        .isEqualTo("customer");
  }

  @Test
  void corruptIndexIsSkipped(@TempDir Path temp) throws IOException {
    URL resource = Files.writeString(temp.resolve("broken.idx"), "not an index").toUri().toURL();
    ClassLoader loader =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            return name.equals("META-INF/jandex.idx")
                ? Collections.enumeration(List.of(resource))
                : super.getResources(name);
          }
        };
    var loaders = List.of(loader);
    assertThat(TigerJexlExecutor.findIndexedNamespaces(loaders)).isEmpty();
  }
}
