package de.gematik.test.tiger;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.writer.RbelWriter;
import de.gematik.test.tiger.lib.TigerDirector;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class RbelUtil {
    private RbelLogger rbelLogger;
    private RbelWriter rbelWriter;

    public RbelWriter getRbelWriter() {
        assureRbelIsInitialized();
        return rbelWriter;
    }

    public RbelConverter getRbelConverter() {
        assureRbelIsInitialized();
        return rbelLogger.getRbelConverter();
    }

    private synchronized void assureRbelIsInitialized() {
        if (rbelWriter == null) {
            rbelLogger =
                    RbelLogger.build(
                            RbelConfiguration.builder()
                                    .activateAsn1Parsing(true)
                                    .initializers(
                                            Optional.ofNullable(
                                                            TigerDirector.getTigerTestEnvMgr()
                                                                    .getConfiguration()
                                                                    .getTigerProxy()
                                                                    .getKeyFolders())
                                                    .stream()
                                                    .flatMap(List::stream)
                                                    .map(RbelKeyFolderInitializer::new)
                                                    .map(init -> (Consumer<RbelConverter>) init)
                                                    .toList())
                                    .build());
            rbelWriter = new RbelWriter(rbelLogger.getRbelConverter());
        }
    }
}
