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
package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerFileSaveInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import kong.unirest.core.Unirest;
import lombok.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EqualsAndHashCode
public abstract class AbstractTigerProxy implements ITigerProxy, AutoCloseable {

  private static final String FIX_VAU_KEY =
      """
      -----BEGIN PRIVATE KEY-----
      MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIAeOzpSQT8a/mQDM
      7Uxa9NzU++vFhbIFS2Nsw/djM73uoUQDQgAEIfr+3Iuh71R3mVooqXlPhjVd8wXx
      9Yr8iPh+kcZkNTongD49z2cL0wXzuSP5Fb/hGTidhpw1ZYKMib1CIjH59A==
      -----END PRIVATE KEY-----
      """;
  private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
  @Getter private final TigerProxyConfiguration tigerProxyConfiguration;
  @Getter private RbelLogger rbelLogger;
  @Getter private RbelFileWriter rbelFileWriter;
  @Getter private Optional<String> name;
  @Getter protected final Logger log;
  @Getter private boolean isShuttingDown = false;

  @Getter
  private ExecutorService executor =
      Executors.newCachedThreadPool(
          r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName("TigerProxy" + getName().map(n -> "-" + n).orElse("") + "-" + t.getId());
            return t;
          });

  @Setter(AccessLevel.PRIVATE)
  private CompletableFuture<Void> fileParsingFuture;

  protected AbstractTigerProxy(TigerProxyConfiguration configuration) {
    this(configuration, null);
  }

  protected AbstractTigerProxy(
      TigerProxyConfiguration configuration, @Nullable RbelLogger rbelLogger) {
    final String loggerName =
        StringUtils.isNotBlank(configuration.getName()) ? "(" + configuration.getName() + ")" : "";
    log = LoggerFactory.getLogger(this.getClass().getName() + loggerName);
    name = Optional.ofNullable(configuration.getName());
    if (configuration.getTls() == null) {
      throw new TigerProxyStartupException("no TLS-configuration found!");
    }
    if (rbelLogger == null) {
      this.rbelLogger = buildRbelLoggerConfiguration(configuration).constructRbelLogger();
    } else {
      this.rbelLogger = rbelLogger;
    }
    this.rbelLogger.getRbelConverter().setName(proxyName());
    addTransmissionPluginToRbelIfNotPresentAlready();
    if (!configuration.isActivateRbelParsing()) {
      this.rbelLogger.getRbelConverter().deactivateRbelParsing();
    }
    addFixVauKey();
    initializeFileWriter();
    this.tigerProxyConfiguration = configuration;
    addNoteCriterions();
    if (configuration.getFileSaveInfo() != null
        && StringUtils.isNotEmpty(configuration.getFileSaveInfo().getSourceFile())) {
      readTrafficFromSourceFile(configuration.getFileSaveInfo().getSourceFile());
    } else {
      fileParsingFuture = CompletableFuture.completedFuture(null);
    }
  }

  private void addTransmissionPluginToRbelIfNotPresentAlready() {
    final boolean noPluginAddedYet =
        rbelLogger.getRbelConverter().getConverterPlugins().stream()
            .filter(TigerProxyRemoteTransmissionConversionPlugin.class::isInstance)
            .map(TigerProxyRemoteTransmissionConversionPlugin.class::cast)
            .filter(this::isTigerProxyMatching)
            .findAny()
            .isEmpty();

    if (noPluginAddedYet) {
      rbelLogger
          .getRbelConverter()
          .addConverter(new TigerProxyRemoteTransmissionConversionPlugin(this));
    }
  }

  protected boolean isTigerProxyMatching(TigerProxyRemoteTransmissionConversionPlugin plugin) {
    return plugin.getTigerProxy() == this;
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
  }

  private void readTrafficFromSourceFile(String sourceFile) {
    fileParsingFuture =
        CompletableFuture.runAsync(() -> readTrafficFromTgrFile(sourceFile))
            .exceptionally(
                e -> {
                  log.error(
                      "Error while reading traffic from file '{}': {}", sourceFile, e.getMessage());
                  throw new TigerProxyStartupException(
                      "Error while reading traffic from file '" + sourceFile + "'", e);
                });
  }

  public synchronized List<RbelElement> readTrafficFromTgrFile(String sourceFile) {
    log.info("Trying to read traffic from file '{}'...", sourceFile);
    try {
      List<RbelElement> readElements = readTraffic(Path.of(sourceFile).toFile());
      log.info("Successfully read and parsed traffic from file '{}'!", sourceFile);
      return readElements;
    } catch (IOException | RuntimeException e) {
      throw new TigerProxyStartupException(
          "Error while parsing traffic file '" + sourceFile + "'", e);
    }
  }

  public synchronized List<RbelElement> readTrafficFromString(String tgrFileContent) {
    return rbelFileWriter.convertFromRbelFile(
        tgrFileContent,
        Optional.ofNullable(getTigerProxyConfiguration().getFileSaveInfo())
            .map(TigerFileSaveInfo::getReadFilter)
            .filter(StringUtils::isNotBlank));
  }

  public synchronized List<RbelElement> readTraffic(File tgrFileContent) throws IOException {
    return rbelFileWriter.convertFromRbelFile(
        new FileReader(tgrFileContent),
        Optional.ofNullable(getTigerProxyConfiguration().getFileSaveInfo())
            .map(TigerFileSaveInfo::getReadFilter)
            .filter(StringUtils::isNotBlank),
        null);
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

  public void triggerListener(RbelElement element, RbelMessageMetadata metadata) {
    executor.submit(
        () ->
            rbelMessageListeners.forEach(listener -> listener.triggerNewReceivedMessage(element)));
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
      String statusUrl = url;
      if (getTigerProxyConfiguration().isRequireHealthyTrafficEndpoints()) {
        statusUrl += "/actuator/health";
      }
      final int status =
          Unirest.get(statusUrl)
              .requestTimeout(getTigerProxyConfiguration().getConnectionTimeoutInSeconds() * 1000)
              .asEmpty()
              .getStatus();
      log.trace("Tiger-proxy at '{}' is online! (status is {})", url, status);
      if (getTigerProxyConfiguration().isRequireHealthyTrafficEndpoints()) {
        return status == HttpStatus.SC_OK;
      } else {
        return true;
      }
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

  public Deque<RbelElement> getRbelMessages() {
    return getRbelLogger().getMessageHistory();
  }

  /*
   * This method is used to ensure that the file is parsed before the proxy is started.
   * It is only called by the TigerTestEnvMgr.
   */
  public void ensureFileIsParsed() {
    try {
      fileParsingFuture.get();
    } catch (CancellationException | ExecutionException e) {
      throw new TigerProxyStartupException("Error while parsing tgr file", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TigerProxyStartupException("Error while parsing tgr file", e);
    }
  }

  public void addRemovedMessageUuidsHandler(Consumer<List<String>> handleRemovedMessageUuids) {
    rbelLogger
        .getRbelConverter()
        .getKnownMessageUuids()
        .addRemovedMessageUuidsHandler(handleRemovedMessageUuids);
  }
}
