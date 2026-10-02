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

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.test.tiger.common.util.TigerVersionProvider;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.val;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Provenance of a rendered Rbel log: which Tiger built it, which optional parsers were active while
 * the traffic was converted, and the configuration it ran with.
 *
 * <p>Without this a report is not self-describing: traffic that a parser would have decrypted looks
 * exactly like traffic that could not be decrypted when the parser was simply never activated.
 */
@Data
@Builder(toBuilder = true)
public class RbelReportMetadata {

  private final String tigerVersion;

  /** Parser ids that were active during conversion, e.g. {@code asl}, {@code websocket}. */
  @Singular private final List<String> activeParsers;

  /**
   * Parser ids that this build knows about but that were not activated. The counterpart to {@link
   * #activeParsers} - an id showing up here explains missing facets far better than their absence.
   */
  @Singular private final List<String> inactiveParsers;

  /** Free-form configuration, rendered as a table and as YAML. Iteration order is preserved. */
  @Singular("configurationEntry")
  private final Map<String, String> configuration;

  public static RbelReportMetadata empty() {
    return RbelReportMetadata.builder().build();
  }

  /** Reads version and parser activation straight off the converter that did the parsing. */
  public static RbelReportMetadata fromConverter(RbelConverter converter) {
    val active = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    converter.getPlugins(RbelConverterPlugin::isOptional).stream()
        .map(RbelConverterPlugin::getParserIdentifiers)
        .forEach(active::addAll);
    val inactive = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
    inactive.addAll(converter.getKnownOptionalParsers());
    inactive.removeAll(active);
    return RbelReportMetadata.builder()
        .tigerVersion(TigerVersionProvider.getTigerVersionString())
        .activeParsers(List.copyOf(active))
        .inactiveParsers(List.copyOf(inactive))
        .build();
  }

  public boolean isEmpty() {
    return (tigerVersion == null || tigerVersion.isBlank())
        && activeParsers.isEmpty()
        && inactiveParsers.isEmpty()
        && configuration.isEmpty();
  }

  /** The same information as the rendered table, ready to paste into a bug report. */
  public String toYaml() {
    val document = new LinkedHashMap<String, Object>();
    if (tigerVersion != null && !tigerVersion.isBlank()) {
      document.put("tigerVersion", tigerVersion);
    }
    if (!activeParsers.isEmpty()) {
      document.put("activeParsers", activeParsers);
    }
    if (!inactiveParsers.isEmpty()) {
      document.put("inactiveParsers", inactiveParsers);
    }
    if (!configuration.isEmpty()) {
      document.put("configuration", configuration);
    }
    if (document.isEmpty()) {
      return "";
    }
    return new Yaml(blockStyleWithIndentedLists()).dump(document);
  }

  /** Lists indented under their key, the shape a reader expects when pasting this into a ticket. */
  private static DumperOptions blockStyleWithIndentedLists() {
    val options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setIndent(2);
    options.setIndicatorIndent(2);
    options.setIndentWithIndicator(true);
    return options;
  }

  /** Flattens nested maps/lists into dotted keys, the shape the configuration table expects. */
  public static Map<String, String> flatten(Map<String, ?> nested) {
    val flat = new LinkedHashMap<String, String>();
    flattenInto("", nested, flat);
    return flat;
  }

  private static void flattenInto(String prefix, Map<String, ?> nested, Map<String, String> flat) {
    nested.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(
            entry -> {
              val key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
              val value = entry.getValue();
              if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                val typedMap = (Map<String, ?>) map;
                flattenInto(key, typedMap, flat);
              } else if (value instanceof List<?> list) {
                flat.put(key, renderList(list));
              } else {
                flat.put(key, String.valueOf(value));
              }
            });
  }

  private static String renderList(List<?> list) {
    return list.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
  }
}
