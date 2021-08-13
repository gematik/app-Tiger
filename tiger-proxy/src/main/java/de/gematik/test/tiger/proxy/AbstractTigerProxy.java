/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.util.CryptoLoader;
import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.util.ClassUtils;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.Key;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public abstract class AbstractTigerProxy implements ITigerProxy {

    private final List<IRbelMessageListener> rbelMessageListeners = new ArrayList<>();
    private RbelLogger rbelLogger;

    public AbstractTigerProxy(TigerProxyConfiguration configuration) {
        hydrateConfigurationWithServerRootCaFiles(configuration);
        rbelLogger = buildRbelLoggerConfiguration(configuration)
            .constructRbelLogger();
    }

    private RbelConfiguration buildRbelLoggerConfiguration(TigerProxyConfiguration configuration) {
        final RbelConfiguration rbelConfiguration = new RbelConfiguration();
        if (configuration.getKeyFolders() != null) {
            configuration.getKeyFolders().stream()
                .forEach(folder -> rbelConfiguration.addInitializer(new RbelKeyFolderInitializer(folder)));
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

    private void hydrateConfigurationWithServerRootCaFiles(TigerProxyConfiguration configuration) {
        if (StringUtils.isNotEmpty(configuration.getServerRootCaP12File())) {
            log.info("Loading server-CA-identity from file {} using password {}...",
                configuration.getServerRootCaP12File(),
                configuration.getServerRootCaP12Pw());
            try {
                final RbelPkiIdentity ca = CryptoLoader.getIdentityFromP12(
                    loadFileOrResourceData(configuration.getServerRootCaP12File()),
                    configuration.getServerRootCaP12Pw());
                configuration.setServerRootCa(ca);
            } catch (Exception e) {
                throw new TigerProxyKeyLoadingException("Error during P12-loading from file '" +
                    configuration.getServerRootCaP12File()
                    + "' using password '" +
                    configuration.getServerRootCaP12Pw()
                    + "'", e);
            }
        } else if (StringUtils.isNotEmpty(configuration.getServerRootCaCertPem())) {
            log.info("Loading server-CA-identity from cert-file {} and private-key-file {}...",
                configuration.getServerRootCaCertPem(),
                configuration.getServerRootCaKeyPem());
            byte[] certificateData;
            byte[] keyData;
            RbelPkiIdentity rootCa;
            try {
                certificateData = loadFileOrResourceData(configuration.getServerRootCaCertPem());
                keyData = loadFileOrResourceData(configuration.getServerRootCaKeyPem());
            } catch (Exception e) {
                throw new TigerProxyKeyLoadingException("Error during Root-CA-loading from files '" +
                    configuration.getServerRootCaCertPem() + "' (PEM, certificate) and '" +
                    configuration.getServerRootCaKeyPem() + "' (unencrypted PKCS8)", e);
            }
            try {
                rootCa = CryptoLoader.getIdentityFromPemAndPkcs8(certificateData, keyData);
            } catch (Exception e1) {
                try {
                    rootCa = CryptoLoader.getIdentityFromPemAndPkcs1(certificateData, keyData);
                } catch (Exception e2) {
                    throw new TigerProxyKeyLoadingException("Error during Root-CA-loading from files '" +
                        configuration.getServerRootCaCertPem() + "' (PEM, certificate) and '" +
                        configuration.getServerRootCaKeyPem() + "' (unencrypted PKCS8)", e2);
                }
            }
            configuration.setServerRootCa(rootCa);
        }
    }

    private byte[] loadFileOrResourceData(final String entityLocation) throws IOException {
        if (StringUtils.isEmpty(entityLocation)) {
            throw new IllegalArgumentException("Trying to load data from empty location! (value is '" + entityLocation + "')");
        }
        if (!entityLocation.startsWith("classpath:") && new File(entityLocation).exists()){
            return FileUtils.readFileToByteArray(new File(entityLocation));
        }
        if (entityLocation.startsWith("classpath:")) {
            return loadResourceData(entityLocation.replaceFirst("classpath:", ""));
        } else {
            return loadResourceData(entityLocation);
        }
    }

    protected byte[] loadResourceData(String name){
        try (InputStream rawStream = ClassUtils.getResourceAsStream( getClass(), name )){
            return rawStream.readAllBytes();
        } catch (IOException e) {
            throw new TigerProxyKeyLoadingException("Error while loading resource data", e);
        }
    }

    static List<File> getResourceFolderFiles(String folder) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource(folder);
        String path = url.getPath();
        File file = new File(path);
        List<File> files = new ArrayList<>();
        if(file.isDirectory()){
            try {
                Files.walk(file.toPath()).filter(Files::isRegularFile).forEach(f -> files.add(f.toFile()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{
            files.add(file);
        }
        return files;
    }

    private static class TigerProxyKeyLoadingException extends RuntimeException {

        public TigerProxyKeyLoadingException(String s, Exception e) {
            super(s, e);
        }
    }
}
