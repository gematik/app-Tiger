/*
 *  Copyright 2021-2026 gematik GmbH
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
 */
package de.gematik.rbellogger.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class RbelReportMetadataTest {

  private static final String SOME_VERSION = "some-tiger-version";

  @Test
  void emptyMetadataHidesTheInformationButton() {
    val html = new RbelHtmlRenderer().doRender(List.of());

    assertThat(html).doesNotContain("Report metadata");
  }

  @Test
  void metadataIsRenderedAsButtonOverlayAndCopyableYaml() {
    val renderer = new RbelHtmlRenderer();
    val metadata =
        RbelReportMetadata.builder()
            .tigerVersion(SOME_VERSION)
            .activeParser("asl")
            .inactiveParser("epa3-vau")
            .configurationEntry("tigerProxy.name", "local_tiger_proxy")
            .build();
    renderer.setReportMetadata(metadata);

    val html = renderer.doRender(List.of());

    assertThat(html)
        .contains("Report metadata")
        .contains(SOME_VERSION)
        .contains("asl")
        .contains("epa3-vau")
        .contains("local_tiger_proxy")
        .contains(metadata.toYaml());
    assertThat(html)
        .as("the two parser groups are told apart by their heading, not by badge colour alone")
        .contains("Active parsers")
        .contains("Known, but inactive parsers");
  }

  @Test
  void activeAndInactiveParsersAreReadOffTheConverter() {
    val rbelLogger =
        RbelLogger.build(new RbelConfiguration().setActivateRbelParsingFor(List.of("asl")));

    val metadata = RbelReportMetadata.fromConverter(rbelLogger.getRbelConverter());

    assertThat(metadata.getTigerVersion()).isNotBlank();
    assertThat(metadata.getActiveParsers()).contains("asl");
    assertThat(metadata.getInactiveParsers()).contains("epa3-vau", "ldap").doesNotContain("asl");
  }

  @Test
  void yamlCoversEverythingTheTableShows() {
    val metadata =
        RbelReportMetadata.builder()
            .tigerVersion(SOME_VERSION)
            .activeParser("asl")
            .activeParser("websocket")
            .inactiveParser("epa3-vau")
            .configurationEntry("activateRbelParsingFor", "[asl, websocket]")
            .configurationEntry("name", "local_tiger_proxy")
            .build();

    assertThat(metadata.toYaml())
        .isEqualTo(
            """
            tigerVersion: some-tiger-version
            activeParsers:
              - asl
              - websocket
            inactiveParsers:
              - epa3-vau
            configuration:
              activateRbelParsingFor: '[asl, websocket]'
              name: local_tiger_proxy
            """);
  }

  @Test
  void scalarsSurviveAYamlRoundTrip() {
    val tricky =
        Map.of(
            "yamlKeyword", "true",
            "number", "-5",
            "empty", "",
            "padded", " padded ",
            "colonNoSpace", "a:b",
            "hash", "#hash",
            "multiline", "two\nlines");
    val metadata = RbelReportMetadata.builder().configuration(tricky).build();

    val reparsed = new Yaml().<Map<String, Map<String, String>>>load(metadata.toYaml());

    assertThat(reparsed.get("configuration"))
        .as("whatever quoting the emitter picks, reading it back has to yield the value verbatim")
        .containsExactlyInAnyOrderEntriesOf(tricky);
    assertThat(metadata.toYaml())
        .as("a colon only starts a mapping when followed by a space, so this needs no quotes")
        .contains("colonNoSpace: a:b");
  }

  @Test
  void nestedConfigurationIsFlattenedIntoDottedKeys() {
    val proxy = new LinkedHashMap<String, Object>();
    proxy.put("name", "local_tiger_proxy");
    proxy.put("activateRbelParsingFor", List.of("asl", "websocket"));
    proxy.put("tls", Map.of("serverRootCa", "ca.p12"));

    assertThat(RbelReportMetadata.flatten(Map.of("tigerProxy", proxy)))
        .containsExactly(
            Map.entry("tigerProxy.activateRbelParsingFor", "[asl, websocket]"),
            Map.entry("tigerProxy.name", "local_tiger_proxy"),
            Map.entry("tigerProxy.tls.serverRootCa", "ca.p12"));
  }
}
