package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.common.TokenSubstituteHelper;
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
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.SneakyThrows;

import java.net.URI;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

import static de.gematik.test.tiger.proxy.TigerProxy.CA_CERT_ALIAS;

public class HttpGlueCode {

    @ParameterType("GET|POST|DELETE|PUT|OPTIONS")
    public Method requestType(String requestedMethod) {
        return Method.valueOf(requestedMethod);
    }

    @SneakyThrows
    @When("TGR send empty {requestType} request to {string}")
    public void sendEmptyRequest(Method method, String address) {
        givenDefaultSpec()
            .request(method, new URI(address));
    }

    @SneakyThrows
    @When("TGR send empty {requestType} request to {string} with headers:")
    public void sendEmptyRequestWithHeaders(Method method, String address, DataTable table) {
        Map<String, String> defaultHeaders = TigerGlobalConfiguration.readMap("tiger", "httpClient", "defaultHeader");
        defaultHeaders.putAll(table.asMap());
        givenDefaultSpec()
            .headers(defaultHeaders)
            .request(method, new URI(address));
    }

    @SneakyThrows
    @When("TGR send {requestType} request with {string} to {string}")
    public void sendRequestWithBody(Method method, String message, String address) {
        givenDefaultSpec()
            .body(TigerGlobalConfiguration.resolvePlaceholders(message))
            .request(method, new URI(address));
    }

    @When("TGR set default header {string} to {string}")
    public void addDefaultHeader(String headerKey, String headerValue) {
        TigerGlobalConfiguration.putValue("tiger.httpClient.defaultHeader." + headerKey, headerValue);
    }

    /**
     * Note the string format.
     *
     * @param pathAndPassword this/is/path;password
     */
    @SneakyThrows
    @When("TGR set default TLS client certificate to {string}")
    public void setDefaultTls(String pathAndPassword) {
        TigerPkiIdentity identity = new TigerPkiIdentity(pathAndPassword);
        RestAssured.trustStore(buildKeyStore(identity));
        var path = pathAndPassword.split(";")[0];
        var password = pathAndPassword.split(";")[1];
        RestAssured.keyStore(path, password);
        int proxyPort = TigerDirector.getTigerTestEnvMgr().getLocalTigerProxyOrFail().getProxyPort();
        RestAssured.proxy("winstone", proxyPort);
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
    public void sendPostRequestToWith(Method method, String url, DataTable dataTable) {
        if (dataTable.asMaps().size() != 1) {
            throw new AssertionError("Expected exactly one entry for datatable, "
                + "got "+dataTable.asMaps().size());
        }

        final Map<String, String> resolvedValuesMap = dataTable.asMaps().get(0).entrySet().stream()
            .map(entry -> Pair.of(
                TigerGlobalConfiguration.resolvePlaceholders(entry.getKey()),
                TigerGlobalConfiguration.resolvePlaceholders(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        givenDefaultSpec()
            .formParams(resolvedValuesMap)
            .request(method, new URI(url));
    }
}
