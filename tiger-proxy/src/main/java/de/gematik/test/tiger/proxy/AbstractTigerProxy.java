/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.file.RbelFileWriter.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.TracingMessagePairFacet;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.certificate.TlsFacet;
import de.gematik.test.tiger.proxy.client.ProxyFileReadingFilter;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import kong.unirest.core.Unirest;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

@Data
public abstract class AbstractTigerProxy implements ITigerProxy, AutoCloseable {

  static {
    TlsFacet.init();
  }

  private static final String FIX_VAU_KEY =
      """
            -----BEGIN PRIVATE KEY-----
            MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIAeOzpSQT8a/mQDM
            7Uxa9NzU++vFhbIFS2Nsw/djM73uoUQDQgAEIfr+3Iuh71R3mVooqXlPhjVd8wXx
            9Yr8iPh+kcZkNTongD49z2cL0wXzuSP5Fb/hGTidhpw1ZYKMib1CIjH59A==
            -----END PRIVATE KEY-----
            """;
  private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
  private final TigerProxyConfiguration tigerProxyConfiguration;
  private RbelLogger rbelLogger;
  private RbelFileWriter rbelFileWriter;
  @Getter private Optional<String> name;
  @Getter protected final org.slf4j.Logger log;
  @Getter private final ExecutorService trafficParserExecutor = Executors.newCachedThreadPool();
  private AtomicBoolean fileParsedCompletely = new AtomicBoolean(false);
  private AtomicReference<RuntimeException> fileParsingException = new AtomicReference<>();
  private boolean isShuttingDown = false;

  protected AbstractTigerProxy(TigerProxyConfiguration configuration) {
    this(configuration, null);
  }

  protected AbstractTigerProxy(
      TigerProxyConfiguration configuration, @Nullable RbelLogger rbelLogger) {
    final String loggerName =
        StringUtils.isNotBlank(configuration.getName()) ? "(" + configuration.getName() + ")" : "";
    log = LoggerFactory.getLogger(this.getClass().getSimpleName() + loggerName);
    name = Optional.ofNullable(configuration.getName());
    if (configuration.getTls() == null) {
      throw new TigerProxyStartupException("no TLS-configuration found!");
    }
    if (rbelLogger == null) {
      this.rbelLogger = buildRbelLoggerConfiguration(configuration).constructRbelLogger();
    } else {
      this.rbelLogger = rbelLogger;
    }
    if (!configuration.isActivateRbelParsing()) {
      this.rbelLogger.getRbelConverter().removeAllConverterPlugins();
    }
    addFixVauKey();
    initializeFileWriter();
    this.tigerProxyConfiguration = configuration;
    addNoteCriterions();
    if (configuration.getFileSaveInfo() != null
        && StringUtils.isNotEmpty(configuration.getFileSaveInfo().getSourceFile())) {
      readTrafficFromSourceFile(configuration.getFileSaveInfo().getSourceFile());
    } else {
      fileParsedCompletely.set(true);
    }
  }

  private void addNoteCriterions() {
    if (tigerProxyConfiguration.getNotes() == null) {
      return;
    }
    tigerProxyConfiguration
        .getNotes()
        .forEach(
            note ->
                rbelLogger
                    .getValueShader()
                    .addJexlNoteCriterion(note.getJexlCriterion(), note.getMessage()));
  }

  private void initializeFileWriter() {
    rbelFileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());
    rbelFileWriter.preSaveListener.add(
        (el, json) ->
            el.getFacet(TracingMessagePairFacet.class)
                .filter(pairFacet -> pairFacet.getResponse().equals(el))
                .ifPresent(
                    pairFacet -> json.put(PAIRED_MESSAGE_UUID, pairFacet.getRequest().getUuid())));
  }

  private void readTrafficFromSourceFile(String sourceFile) {
    new Thread(
            () -> {
              try {
                readTrafficFromTgrFile(sourceFile);
                fileParsedCompletely.set(true);
              } catch (RuntimeException e) {
                fileParsingException.set(e);
              }
            },
            "readTrafficFromSourceFile")
        .start();
  }

  public synchronized List<RbelElement> readTrafficFromTgrFile(String sourceFile) {
    log.info("Trying to read traffic from file '{}'...", sourceFile);
    try {
      String rbelFileContent = Files.readString(Path.of(sourceFile), StandardCharsets.UTF_8);
      List<RbelElement> readElements = readTrafficFromString(rbelFileContent);
      log.info("Successfully read and parsed traffic from file '{}'!", sourceFile);
      return readElements;
    } catch (IOException | RuntimeException e) {
      throw new TigerProxyStartupException(
          "Error while parsing traffic file '" + sourceFile + "'", e);
    }
  }

  public synchronized List<RbelElement> readTrafficFromString(String tgrFileContent) {
    try {
      rbelFileWriter.postConversionListener.add(TracingMessagePairFacet.pairingPostProcessor);
      rbelFileWriter.postConversionListener.add(
          TracingMessagePairFacet.updateHttpFacetsBasedOnPairsPostProcessor);
      if (getTigerProxyConfiguration().getFileSaveInfo() != null
          && StringUtils.isNotEmpty(
              getTigerProxyConfiguration().getFileSaveInfo().getReadFilter())) {
        rbelFileWriter.postConversionListener.add(
            new ProxyFileReadingFilter(
                getTigerProxyConfiguration().getFileSaveInfo().getReadFilter()));
      }
      return rbelFileWriter.convertFromRbelFile(tgrFileContent);
    } finally {
      rbelFileWriter.postConversionListener.remove(TracingMessagePairFacet.pairingPostProcessor);
      rbelFileWriter.postConversionListener.remove(
          TracingMessagePairFacet.updateHttpFacetsBasedOnPairsPostProcessor);
    }
  }

  private void addFixVauKey() {
    final KeyPair keyPair =
        KeyMgr.readEcdsaKeypairFromPkcs8Pem(FIX_VAU_KEY.getBytes(StandardCharsets.UTF_8));
    final RbelKey rbelPublicVauKey =
        RbelKey.builder()
            .keyName("fixVauKey_public")
            .key(keyPair.getPublic())
            .precedence(0)
            .build();
    final RbelKey rbelPrivateVauKey =
        RbelKey.builder()
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
      configuration
          .getKeyFolders()
          .forEach(
              folder -> rbelConfiguration.addInitializer(new RbelKeyFolderInitializer(folder)));
    }

    if (configuration.getActivateRbelParsingFor() != null) {
      configuration.getActivateRbelParsingFor().forEach(rbelConfiguration::activateConversionFor);
    }
    initializeFileSaver(configuration);
    rbelConfiguration.setRbelBufferSizeInMb(configuration.getRbelBufferSizeInMb());
    rbelConfiguration.setSkipParsingWhenMessageLargerThanKb(
        configuration.getSkipParsingWhenMessageLargerThanKb());
    rbelConfiguration.setManageBuffer(true);
    return rbelConfiguration;
  }

  private void initializeFileSaver(TigerProxyConfiguration configuration) {
    if (configuration.getFileSaveInfo() != null
        && configuration.getFileSaveInfo().isWriteToFile()) {
      if (configuration.getFileSaveInfo().isClearFileOnBoot()
          && new File(configuration.getFileSaveInfo().getFilename()).exists()) {
        try {
          FileUtils.delete(new File(configuration.getFileSaveInfo().getFilename()));
        } catch (IOException e) {
          throw new TigerProxyStartupException(
              "Error while deleting file on startup '"
                  + configuration.getFileSaveInfo().getFilename()
                  + "'");
        }
      }
      addRbelMessageListener(
          msg -> {
            final String msgString = rbelFileWriter.convertToRbelFileString(msg);
            try {
              FileUtils.writeStringToFile(
                  new File(configuration.getFileSaveInfo().getFilename()),
                  msgString,
                  StandardCharsets.UTF_8,
                  true);
            } catch (IOException e) {
              log.warn(
                  "Error while saving to file '"
                      + configuration.getFileSaveInfo().getFilename()
                      + "':",
                  e);
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
    try {
      getRbelMessageListeners().forEach(listener -> listener.triggerNewReceivedMessage(element));
    } finally {
      RbelConverter.setMessageFullyProcessed(element);
    }
  }

  @Override
  public void addRbelMessageListener(IRbelMessageListener listener) {
    rbelMessageListeners.add(listener);
  }

  @Override
  public void clearAllRoutes() {
    getRoutes().stream()
        .filter(route -> !route.isInternalRoute())
        .map(TigerProxyRoute::getId)
        .forEach(this::removeRoute);
  }

  @Override
  public void removeRbelMessageListener(IRbelMessageListener listener) {
    rbelMessageListeners.remove(listener);
  }

  protected void waitForRemoteTigerProxyToBeOnline(String url) {
    LocalDateTime pollingStart = LocalDateTime.now();
    while (!isShuttingDown && !isGivenTigerProxyHealthy(url)) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        log.debug("InterruptedException while waiting for remote-proxy '{}' to be healthy", url);
        Thread.currentThread().interrupt();
      }
      if (pollingStart
          .plus(getTigerProxyConfiguration().getConnectionTimeoutInSeconds(), ChronoUnit.SECONDS)
          .isBefore(LocalDateTime.now())) {
        throw new TigerProxyStartupException(
            "Timeout while waiting for remote-proxy '" + url + "' to be healthy");
      }
    }
    if (isShuttingDown) {
      log.warn("Aborting waitForRemoteTigerProxyToBeOnline at '{}'", url);
    }
  }

  @SneakyThrows
  private boolean isGivenTigerProxyHealthy(String url) {
    try {
      log.debug("Waiting for tiger-proxy at '{}' to be online...", url);
      final int status =
          Unirest.get(url + "/actuator/health")
              .requestTimeout(getTigerProxyConfiguration().getConnectionTimeoutInSeconds() * 1000)
              .asEmpty()
              .getStatus();
      log.trace("Tiger-proxy at '{}' is online! (status is {})", url, status);
      return status == 200;
    } catch (RuntimeException e) {
      return false;
    }
  }

  public String proxyName() {
    return name.orElse("");
  }

  public void clearAllMessages() {
    getRbelLogger().getRbelConverter().clearAllMessages();
  }

  public Boolean isFileParsed() {
    if (fileParsingException.get() != null) {
      throw fileParsingException.get();
    }
    return fileParsedCompletely.get();
  }

  public Deque<RbelElement> getRbelMessages() {
    return getRbelLogger().getMessageHistory();
  }

  @Override
  public void close() {
    isShuttingDown = true;
    trafficParserExecutor.shutdown();
  }
}
