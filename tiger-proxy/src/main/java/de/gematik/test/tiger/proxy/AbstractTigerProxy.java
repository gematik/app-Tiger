/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.pki.KeyMgr;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public abstract class AbstractTigerProxy implements ITigerProxy {

    private static final String FIX_VAU_KEY = "-----BEGIN PRIVATE KEY-----\n" +
        "MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIAeOzpSQT8a/mQDM\n" +
        "7Uxa9NzU++vFhbIFS2Nsw/djM73uoUQDQgAEIfr+3Iuh71R3mVooqXlPhjVd8wXx\n" +
        "9Yr8iPh+kcZkNTongD49z2cL0wXzuSP5Fb/hGTidhpw1ZYKMib1CIjH59A==\n" +
        "-----END PRIVATE KEY-----\n";
    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
    private final TigerProxyConfiguration tigerProxyConfiguration;
    private RbelLogger rbelLogger;

    public AbstractTigerProxy(TigerProxyConfiguration configuration) {
        if (configuration.getTls() == null) {
            throw new TigerProxyStartupException("no TLS-configuration found!");
        }
        rbelLogger = buildRbelLoggerConfiguration(configuration)
            .constructRbelLogger();
        if (!configuration.isActivateRbelParsing()) {
            rbelLogger.getRbelConverter().removeAllConverterPlugins();
        }
        addFixVauKey();
        this.tigerProxyConfiguration = configuration;
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
            .key(keyPair.getPublic())
            .precedence(0)
            .matchingPublicKey(rbelPublicVauKey)
            .build();
        rbelLogger.getRbelKeyManager().addKey(rbelPublicVauKey);
        rbelLogger.getRbelKeyManager().addKey(rbelPrivateVauKey);
    }

    private RbelConfiguration buildRbelLoggerConfiguration(TigerProxyConfiguration configuration) {
        final RbelConfiguration rbelConfiguration = new RbelConfiguration();
        if (configuration.getKeyFolders() != null) {
            configuration.getKeyFolders().forEach(folder -> rbelConfiguration.addInitializer(new RbelKeyFolderInitializer(folder)));
        }
        rbelConfiguration.setFileSaveInfo(configuration.getFileSaveInfo());
        rbelConfiguration.setActivateAsn1Parsing(configuration.isActivateAsn1Parsing());
        return rbelConfiguration;
    }

    @Override
    public List<RbelElement> getRbelMessages() {
        return rbelLogger.getMessageHistory();
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
}