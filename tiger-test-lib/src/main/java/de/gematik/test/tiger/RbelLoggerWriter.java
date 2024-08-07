/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;

@Getter
public class RbelLoggerWriter {
  private final RbelLogger rbelLogger;
  private final RbelWriter rbelWriter;

  public RbelLoggerWriter() {
    this.rbelLogger = buildRbelLogger();
    this.rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
  }

  public RbelConverter getRbelConverter() {
    return rbelLogger.getRbelConverter();
  }

  private RbelLogger buildRbelLogger() {
    return RbelLogger.build(
        RbelConfiguration.builder()
            .initializers(
                listKeyFolders().stream()
                    .map(RbelKeyFolderInitializer::new)
                    .map(init -> (Consumer<RbelConverter>) init)
                    .toList())
            .build()
            .activateConversionFor("asn1"));
  }

  @SuppressWarnings("unchecked")
  private List<String> listKeyFolders() {
    return TigerGlobalConfiguration.instantiateConfigurationBean(
            List.class, "tiger.tigerProxy.keyFolders")
        .orElse(List.of());
  }
}
