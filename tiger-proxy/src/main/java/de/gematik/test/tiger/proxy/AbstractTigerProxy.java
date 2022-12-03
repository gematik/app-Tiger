/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelErpVauDecrpytionConverter;
import de.gematik.rbellogger.converter.RbelVauEpaConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.RbelFileWriter;
import de.gematik.rbellogger.util.RbelMessagePostProcessor;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.client.ProxyFileReadingFilter;
import de.gematik.test.tiger.proxy.data.TracingMessagePairFacet;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import de.gematik.test.tiger.proxy.vau.RbelVauSessionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import kong.unirest.Unirest;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

@Data
public abstract class AbstractTigerProxy implements ITigerProxy {

    public static final String PAIRED_MESSAGE_UUID = "pairedMessageUuid";
    private static final RbelMessagePostProcessor pairingPostProcessor = (el, conv, json) -> {
        if (json.has(PAIRED_MESSAGE_UUID)) {
            final String partnerUuid = json.getString(PAIRED_MESSAGE_UUID);
            final Optional<RbelElement> partner = conv.messagesStreamLatestFirst()
                .filter(element -> element.getUuid().equals(partnerUuid))
                .findFirst();
            if (partner.isPresent()) {
                final TracingMessagePairFacet pairFacet = TracingMessagePairFacet.builder()
                    .response(el)
                    .request(partner.get())
                    .build();
                el.addFacet(pairFacet);
                partner.get().addFacet(pairFacet);
            }
        }
    };
    private static final String FIX_VAU_KEY = "-----BEGIN PRIVATE KEY-----\n" +
        "MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIAeOzpSQT8a/mQDM\n" +
        "7Uxa9NzU++vFhbIFS2Nsw/djM73uoUQDQgAEIfr+3Iuh71R3mVooqXlPhjVd8wXx\n" +
        "9Yr8iPh+kcZkNTongD49z2cL0wXzuSP5Fb/hGTidhpw1ZYKMib1CIjH59A==\n" +
        "-----END PRIVATE KEY-----\n";
    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
    private final TigerProxyConfiguration tigerProxyConfiguration;
    private RbelLogger rbelLogger;
    private RbelFileWriter rbelFileWriter;
    private Optional<String> name;
    @Getter
    protected final org.slf4j.Logger log;
    @Getter
    private final ExecutorService trafficParserExecutor = Executors.newSingleThreadExecutor();
    private AtomicBoolean fileParsedCompletely = new AtomicBoolean(false);

    public AbstractTigerProxy(TigerProxyConfiguration configuration) {
        this(configuration, null);
    }

    public AbstractTigerProxy(TigerProxyConfiguration configuration, @Nullable RbelLogger rbelLogger) {
        log = LoggerFactory.getLogger(AbstractTigerProxy.class);
        name = Optional.ofNullable(configuration.getName());
        if (configuration.getTls() == null) {
            throw new TigerProxyStartupException("no TLS-configuration found!");
        }
        if (rbelLogger == null) {
            this.rbelLogger = buildRbelLoggerConfiguration(configuration)
                .constructRbelLogger();
        } else {
            this.rbelLogger = rbelLogger;
        }
        if (!configuration.isActivateRbelParsing()) {
            this.rbelLogger.getRbelConverter().removeAllConverterPlugins();
        }
        addFixVauKey();
        initializeFileWriter();
        this.tigerProxyConfiguration = configuration;
        if (configuration.getFileSaveInfo() != null
            && StringUtils.isNotEmpty(configuration.getFileSaveInfo().getSourceFile())) {
            readTrafficFromSourceFile(configuration.getFileSaveInfo().getSourceFile());
        } else {
            fileParsedCompletely.set(true);
        }
    }

    private void initializeFileWriter() {
        rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());
        rbelFileWriter.preSaveListener.add((el, json) ->
            el.getFacet(TracingMessagePairFacet.class)
                .filter(pairFacet -> pairFacet.getResponse().equals(el))
                .ifPresent(pairFacet -> json.put(
                    PAIRED_MESSAGE_UUID,
                    pairFacet.getRequest().getUuid())));

    }

    protected void readTrafficFromSourceFile(String sourceFile) {
        CompletableFuture.supplyAsync(() -> {
            log.info("Trying to read traffic from file '{}'...", sourceFile);
            try {
                rbelFileWriter.postConversionListener.add(pairingPostProcessor);
                if (StringUtils.isNotEmpty(getTigerProxyConfiguration().getFileSaveInfo().getReadFilter())) {
                    rbelFileWriter.postConversionListener.add(
                        new ProxyFileReadingFilter(getTigerProxyConfiguration().getFileSaveInfo().getReadFilter()));
                }
                rbelFileWriter.convertFromRbelFile(
                    Files.readString(Path.of(sourceFile), StandardCharsets.UTF_8));
                log.info("Successfully read and parsed traffic from file '{}'!", sourceFile);
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                rbelFileWriter.postConversionListener.remove(pairingPostProcessor);
            }
        })
            .thenRunAsync(() -> fileParsedCompletely.set(true));
    }

    private void addFixVauKey() {
        final KeyPair keyPair = KeyMgr.readEcdsaKeypairFromPkcs8Pem(FIX_VAU_KEY.getBytes(StandardCharsets.UTF_8));
        final RbelKey rbelPublicVauKey = RbelKey.builder()
            .keyName("fixVauKey_public")
            .key(keyPair.getPublic())
            .precedence(0)
            .build();
        final RbelKey rbelPrivateVauKey = RbelKey.builder()
            .keyName("fixVauKey_public")
            .key(keyPair.getPrivate())
            .precedence(0)
            .matchingPublicKey(rbelPublicVauKey)
            .build();
        rbelLogger.getRbelKeyManager().addKey(rbelPublicVauKey);
        rbelLogger.getRbelKeyManager().addKey(rbelPrivateVauKey);
    }

    private RbelConfiguration buildRbelLoggerConfiguration(TigerProxyConfiguration configuration) {
        final RbelConfiguration rbelConfiguration = new RbelConfiguration();
        if (configuration.getKeyFolders() != null) {
            configuration.getKeyFolders()
                .forEach(folder -> rbelConfiguration.addInitializer(new RbelKeyFolderInitializer(folder)));
        }
        if (configuration.isActivateEpaVauAnalysis()) {
            rbelConfiguration.addPostConversionListener(new RbelVauSessionListener());
            rbelConfiguration.addAdditionalConverter(new RbelVauEpaConverter());
        }
        if (configuration.isActivateErpVauAnalysis()) {
            rbelConfiguration.addAdditionalConverter(new RbelErpVauDecrpytionConverter());
        }
        initializeFileSaver(configuration);
        rbelConfiguration.setActivateAsn1Parsing(configuration.isActivateAsn1Parsing());
        rbelConfiguration.setRbelBufferSizeInMb(configuration.getRbelBufferSizeInMb());
        rbelConfiguration.setSkipParsingWhenMessageLargerThanKb(
            configuration.getSkipParsingWhenMessageLargerThanKb()
        );
        rbelConfiguration.setManageBuffer(true);
        return rbelConfiguration;
    }

    private void initializeFileSaver(TigerProxyConfiguration configuration) {
        if (configuration.getFileSaveInfo() != null && configuration.getFileSaveInfo().isWriteToFile()) {
            if (configuration.getFileSaveInfo().isClearFileOnBoot() &&
                new File(configuration.getFileSaveInfo().getFilename()).exists()) {
                try {
                    FileUtils.delete(new File(configuration.getFileSaveInfo().getFilename()));
                } catch (IOException e) {
                    throw new TigerProxyStartupException("Error while deleting file on startup '"
                        + configuration.getFileSaveInfo().getFilename() + "'");
                }
            }
            addRbelMessageListener(msg -> {
                final String msgString = rbelFileWriter.convertToRbelFileString(msg);
                try {
                    FileUtils.writeStringToFile(new File(configuration.getFileSaveInfo().getFilename()), msgString,
                        StandardCharsets.UTF_8, true);
                } catch (IOException e) {
                    log.warn("Error while saving to file '"
                        + configuration.getFileSaveInfo().getFilename() + "':", e);
                }
            });
        }
    }


    public List<RbelElement> getRbelMessagesList() {
        return rbelLogger.getMessageList();
    }

    @Override
    public void addKey(String keyid, Key key) {
        rbelLogger.getRbelKeyManager().addKey(keyid, key, RbelKey.PRECEDENCE_KEY_FOLDER);
    }

    public void triggerListener(RbelElement element) {
        getRbelMessageListeners()
            .forEach(listener -> listener.triggerNewReceivedMessage(element));
    }

    @Override
    public void addRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.add(listener);
    }

    @Override
    public void clearAllRoutes() {
        getRoutes().stream()
            .filter(route -> !route.isInternalRoute())
            .map(TigerRoute::getId)
            .forEach(this::removeRoute);
    }

    @Override
    public void removeRbelMessageListener(IRbelMessageListener listener) {
        rbelMessageListeners.remove(listener);
    }

    protected void waitForRemoteTigerProxyToBeOnline(String url) {
        await()
            .atMost(getTigerProxyConfiguration().getConnectionTimeoutInSeconds() * 20L, TimeUnit.SECONDS)
            .pollInterval(500, TimeUnit.MILLISECONDS)
            .until(() -> {
                try {
                    log.debug("Waiting for tiger-proxy at '{}' to be online...", url);
                    Unirest.get(url)
                        .connectTimeout(getTigerProxyConfiguration().getConnectionTimeoutInSeconds() * 1000)
                        .asEmpty();
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });
    }

    public String proxyName() {
        return name
            .map(s -> s + ": ")
            .orElse("");
    }

    public void clearAllMessages() {
        getRbelLogger().getRbelConverter().clearAllMessages();
    }

    public List<RbelElement> getRbelMessages() {
        return getRbelLogger().getRbelConverter().getMessageList();
    }

    public Boolean isFileParsed() {
        return fileParsedCompletely.get();
    }
}
