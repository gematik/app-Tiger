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
 */
package de.gematik.test.tiger.proxy.handler.pcap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BpfFilterBuilderTest {

  @Test
  void emptyOrNullInput_yieldsEmptyFilter() {
    assertThat(BpfFilterBuilder.forTcpPorts(null)).isEmpty();
    assertThat(BpfFilterBuilder.forTcpPorts(List.of())).isEmpty();
  }

  @Test
  void singlePort_yieldsSingleClause() {
    assertThat(BpfFilterBuilder.forTcpPorts(List.of(8080))).isEqualTo("tcp port 8080");
  }

  @Test
  void multiplePorts_areJoinedWithOr_inAscendingOrder() {
    assertThat(BpfFilterBuilder.forTcpPorts(List.of(9000, 8080, 443)))
        .isEqualTo("tcp port 443 or tcp port 8080 or tcp port 9000");
  }

  @Test
  void duplicatesAreCollapsed() {
    assertThat(BpfFilterBuilder.forTcpPorts(List.of(8080, 8080, 8080))).isEqualTo("tcp port 8080");
  }

  @Test
  void setInput_worksLikeAnyCollection() {
    assertThat(BpfFilterBuilder.forTcpPorts(Set.of(8080, 8081)))
        .isEqualTo("tcp port 8080 or tcp port 8081");
  }

  @Test
  void invalidPorts_areSkipped() {
    // 0, negative, > 65535 are silently dropped.
    assertThat(BpfFilterBuilder.forTcpPorts(Arrays.asList(0, -1, 65536, 100000, 8080)))
        .isEqualTo("tcp port 8080");
  }

  @Test
  void allInvalidPorts_yieldEmptyFilter() {
    assertThat(BpfFilterBuilder.forTcpPorts(List.of(0, 70000))).isEmpty();
  }
}
