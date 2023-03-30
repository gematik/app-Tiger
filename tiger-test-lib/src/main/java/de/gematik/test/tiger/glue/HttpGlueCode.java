package de.gematik.test.tiger.glue;

import static de.gematik.test.tiger.proxy.TigerProxy.CA_CERT_ALIAS;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.proxy.tls.TlsCertificateGenerator;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
@Slf4j
public class HttpGlueCode {

    @ParameterType("GET|POST|DELETE|PUT|OPTIONS")
    public Method requestType(String requestedMethod) {
        return Method.valueOf(requestedMethod);
    }

    private String resolve(String value) {
        return TigerGlobalConfiguration.resolvePlaceholders(value);
    }
    @SneakyThrows
    @When("TGR send empty {requestType} request to {string}")
    @When("TGR sende eine leere {requestType} Anfrage an {string} schickt")
    public void sendEmptyRequest(Method method, String address) {
        log.info("Sending empty {} request to {}", method, address);
        givenDefaultSpec().request(method, new URI(resolve(address)));
    }

    @SneakyThrows
    @When("TGR send empty {requestType} request to {string} with headers:")
    @When("TGR sende eine leere {requestType} Anfrage an {string} mit folgenden Headern schickt:")
    public void sendEmptyRequestWithHeaders(Method method, String address, DataTable table) {
        log.info("Sending empty {} request with headers to {}", method, address);
        Map<String, String> defaultHeaders = TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader");
        defaultHeaders.putAll(table.asMap());
        givenDefaultSpec()
            .headers(defaultHeaders)
            .request(method, new URI(resolve(address)));
    }

    @SneakyThrows
    @When("TGR send {requestType} request with {string} to {string}")
    @When("TGR sende eine {requestType} Anfrage an {string} mit Body {string} schickt")
    public void sendRequestWithBody(Method method, String message, String address) {
        log.info("Sending {} request with body to {}", method, address);
        givenDefaultSpec()
            .body(resolve(message))
            .request(method, new URI(resolve(address)));
    }

    @When("TGR set default header {string} to {string}")
    @When("TGR setze den default header {string} auf den Wert {string}")
    public void addDefaultHeader(String headerKey, String headerValue) {
        TigerGlobalConfiguration.putValue("tiger.httpClient.defaultHeader." + resolve(headerKey), resolve(headerValue));
    }

    /**
     * Note the string format.
     *
     * @param pathAndPassword this/is/path;password
     */
    @SneakyThrows
    // @When("TGR set default TLS client certificate to {string}")
    // TGR-864
    public void setDefaultTls(String pathAndPassword) {
        TigerPkiIdentity identity = new TigerPkiIdentity(resolve(pathAndPassword));
        RestAssured.trustStore(buildKeyStore(identity));
        var path = pathAndPassword.split(";")[0];
        var password = pathAndPassword.split(";")[1];
        RestAssured.keyStore(path, password);
        int proxyPort = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getProxyPort();
        RestAssured.proxy("winstone", proxyPort); // BUG why winstone as proxy?
        TlsCertificateGenerator.generateNewCaCertificate();
    }

    private static RequestSpecification givenDefaultSpec() {
        return RestAssured
            .given()
            .headers(TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader"));
    }

    @SneakyThrows
    private KeyStore buildKeyStore(TigerPkiIdentity serverIdentity) {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null);
        keyStore.setCertificateEntry(CA_CERT_ALIAS, serverIdentity.getCertificate());
        int chainCertCtr = 0;
        for (final X509Certificate chainCert : serverIdentity.getCertificateChain()) {
            keyStore.setCertificateEntry("chainCert" + chainCertCtr++, chainCert);
        }
        return keyStore;
    }

    @SneakyThrows
    @When("Send {requestType} request to {string} with")
    @When("TGR Send {requestType} request to {string} with:")
    @When("TGR Sende eine {requestType} Anfrage an {string} mit folgenden Daten:")
    public void sendPostRequestToWith(Method method, String address, DataTable dataTable) {
        List<Map<String, String>> dataAsMaps = dataTable.asMaps();
        if (dataAsMaps.size() != 1) {
            throw new AssertionError("Expected exactly one entry for data table, "
                + "got "+ dataAsMaps.size());
        }

        final Map<String, String> resolvedValuesMap = dataAsMaps.get(0).entrySet().stream()
            .map(entry -> Pair.of(
                resolve(entry.getKey()),
                resolve(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        givenDefaultSpec()
            .formParams(resolvedValuesMap)
            .request(method, new URI(resolve(address)));
    }
}
