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
package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tiger.CustomerServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class TigerServerTypeDiscoveryTest {

  @Test
  void discoversCustomerServerAndExcludesAbstractClasses() {
    var indexed = TigerTestEnvMgr.findIndexedServerClasses(List.of(getClass().getClassLoader()));
    assertThat(indexed)
        .containsEntry("customerServer", CustomerServer.class)
        .doesNotContainKey("abstractCustomerServer");
  }

  @Test
  void indexMatchesLegacyScan() throws ClassNotFoundException {
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(TigerServerType.class));
    var scanned = new TreeMap<String, String>();
    for (var bean : scanner.findCandidateComponents("de.gematik.test.tiger")) {
      var type = Class.forName(bean.getBeanClassName(), false, getClass().getClassLoader());
      scanned.put(type.getAnnotation(TigerServerType.class).value(), type.getName());
    }
    var indexed =
        TigerTestEnvMgr.findIndexedServerClasses(List.of(getClass().getClassLoader()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().getName().startsWith("de.gematik.test.tiger."))
            .collect(Collectors.toMap(java.util.Map.Entry::getKey, e -> e.getValue().getName()));
    assertThat(scanned).isNotEmpty();
    assertThat(indexed).containsExactlyInAnyOrderEntriesOf(scanned);
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          localProxyActive: false
          servers:
            customer:
              type: customerServer
          """,
      skipEnvironmentSetup = true)
  void createsConfiguredCustomerServer(TigerTestEnvMgr environment) {
    assertThat(environment.getServers().get("customer")).isInstanceOf(CustomerServer.class);
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
    assertThat(TigerTestEnvMgr.findIndexedServerClasses(loaders)).isEmpty();
  }
}
