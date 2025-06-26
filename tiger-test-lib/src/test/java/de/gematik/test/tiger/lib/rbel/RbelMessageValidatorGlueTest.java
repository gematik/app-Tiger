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
package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelElementAssertion;
import de.gematik.rbellogger.facets.pop3.RbelPop3Command;
import de.gematik.rbellogger.facets.pop3.RbelPop3CommandFacet;
import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.lib.TigerDirector;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelMessageValidatorGlueTest {
  @BeforeEach
  public void setUp() {
    TigerDirector.testUninitialize();
  }

  @AfterEach
  public void cleanUp() {
    TigerDirector.testUninitialize();
    TigerConfigurationKeys.TIGER_YAML_VALUE.clearSystemProperty();
    TigerConfigurationKeys.TIGER_TESTENV_CFGFILE_LOCATION.clearSystemProperty();
  }

  @Test
  void testSharedValidatorInstance() {
    TigerConfigurationKeys.TIGER_YAML_VALUE.setAsSystemProperty(
        """
      localProxyActive: true
      """);
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();
          RBelValidatorGlue glue1 = new RBelValidatorGlue();
          RBelValidatorGlue glue2 = new RBelValidatorGlue();
          Assertions.assertThat(glue1.getRbelMessageRetriever())
              .isSameAs(glue2.getRbelMessageRetriever());
          Assertions.assertThat(glue1.getRbelMessageRetriever())
              .isSameAs(RbelMessageRetriever.getInstance());
          Assertions.assertThat(RbelMessageRetriever.getInstance()).isNotNull();
        });
  }

  @Test
  void testReadTrafficFile() {
    TigerConfigurationKeys.TIGER_YAML_VALUE.setAsSystemProperty(
        """
      localProxyActive: true
      """);
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();

          LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
          RBelValidatorGlue glue = new RBelValidatorGlue();
          glue.readTgrFile("src/test/resources/testdata/rezepsFiltered.tgr");

          assertThat(LocalProxyRbelMessageListener.getInstance().getValidatableRbelMessages())
              .hasSize(96);
        });
  }

  @SneakyThrows
  @Test
  void testDeactivateParser() {
    TigerConfigurationKeys.TIGER_YAML_VALUE.setAsSystemProperty(
        """
        tigerProxy:
            activateRbelParsingFor:
                - pop3
        """);
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();
          var tigerProxy = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail();
          var rbelConverter = tigerProxy.getRbelLogger().getRbelConverter();

          var convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .hasFacet(RbelPop3CommandFacet.class)
              .extractChildWithPath("$.pop3Command")
              .hasValueEqualTo(RbelPop3Command.CAPA);

          RBelValidatorGlue glue = new RBelValidatorGlue();

          glue.deactivateParsingFor("pop3");
          convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .doesNotHaveFacet(RbelPop3CommandFacet.class)
              .doesNotHaveChildWithPath("$.pop3Command");

          glue.activateParsingForAll();
          convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .hasFacet(RbelPop3CommandFacet.class)
              .extractChildWithPath("$.pop3Command")
              .hasValueEqualTo(RbelPop3Command.CAPA);
        });
  }

  @SneakyThrows
  @Test
  void testActivateParser() {
    TigerConfigurationKeys.TIGER_YAML_VALUE.setAsSystemProperty(
        """
        tigerProxy:
            activateRbelParsingFor:
                - pop3
        """);
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();
          var tigerProxy = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail();
          var rbelConverter = tigerProxy.getRbelLogger().getRbelConverter();

          RBelValidatorGlue glue = new RBelValidatorGlue();

          glue.deactivateOptionalParsing();

          var convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .doesNotHaveFacet(RbelPop3CommandFacet.class);

          glue.activateParsingFor("pop3");

          convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .hasFacet(RbelPop3CommandFacet.class)
              .extractChildWithPath("$.pop3Command")
              .hasValueEqualTo(RbelPop3Command.CAPA);

          glue.deactivateOptionalParsing();

          convertedCapaElement =
              rbelConverter.convertElement(
                  RbelElement.builder()
                      .rawContent("CAPA\r\n".getBytes(StandardCharsets.US_ASCII))
                      .build());

          RbelElementAssertion.assertThat(convertedCapaElement)
              .doesNotHaveFacet(RbelPop3CommandFacet.class);
        });
  }

  private void executeWithSecureShutdown(Runnable test) {
    try {
      test.run();
    } finally {
      TigerDirector.getTigerTestEnvMgr().shutDown();
    }
  }
}
