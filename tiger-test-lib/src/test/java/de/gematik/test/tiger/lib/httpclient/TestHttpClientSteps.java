/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.httpclient;

import de.gematik.test.tiger.glue.HttpGlueCode;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.glue.TigerGlue;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.restassured.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Performs all tests as defined in HttpGlueCodeTest.feature as unit tests so that the coverage of the glue code is also added to sonar. Cucumber does some
 * bytebuddy instrumentation magic with the glue code so by running the feature file as test, no coverage is reported for the glue code.
 */
@ExtendWith(MockServerExtension.class)
@Slf4j
public class TestHttpClientSteps {

    private final HttpGlueCode httpGlueCode = new HttpGlueCode();
    private final RBelValidatorGlue rbelValidatorGlueCode = new RBelValidatorGlue();
    private final TigerGlue tigerGlue = new TigerGlue();

    @BeforeAll
    public static void resetTiger() {
        TigerDirector.testUninitialize();
    }

    @BeforeEach
    public synchronized void clearMessages() {
        if (!TigerDirector.isInitialized()) {
            System.setProperty("tiger.testenv.cfgfile", "tiger.yaml");
            TigerDirector.start();
            TigerDirector.getLibConfig().getHttpClientConfig().setActivateRbelWriter(true);
        }
        rbelValidatorGlueCode.tgrClearRecordedMessages();
    }

    @AfterAll
    public static void shutdown() {
        TigerTestEnvMgr tigerTestEnvMgr = TigerDirector.getTigerTestEnvMgr();
        if (tigerTestEnvMgr.isShuttingDown()) {
            return;
        }
        tigerTestEnvMgr.shutDown();
        System.clearProperty("tiger.testenv.cfgfile");
    }

    @Test
    void simpleGetRequest() {
        httpGlueCode.sendEmptyRequest(Method.GET, "http://winstone");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "GET");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/?");
    }

    @Test
    void sendComplexPost() {
        httpGlueCode.sendRequestWithMultiLineBody(Method.POST, "http://winstone",
                """
                        { 
                          "object": { "field": "value" },
                          "array" : [ "1", 2, { "field2": "value2" } ],
                          "member" : "test"
                        }                          
                        """);
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","POST");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "application/json");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.object.field')}", "value");
        tigerGlue.tgrAssertMatches("!{rbel:currentResponseAsString('$.responseCode')}", "200");
    }

    //TODO till https://github.com/jenkinsci/winstone/issues/339
    // is resolved we rely on external httpbin server
    @Test
    void sendComplexPut() {
        httpGlueCode.sendRequestWithMultiLineBody(Method.PUT, "http://httpbin.org/put",
                """
                        { 
                          "object": { "field": "value" },
                          "array" : [ "1", 2, { "field2": "value2" } ],
                          "member" : "test"
                        }                          
                        """);
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "application/json");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.object.field')}", "value");
        tigerGlue.tgrAssertMatches("!{rbel:currentResponseAsString('$.responseCode')}", "200");
    }
    @Test
    void simpleGetRequestNonBlocking(MockServerClient client) {
        AtomicBoolean blockResponse = new AtomicBoolean(true);
        client.when(request()
                .withPath("/blockUntilRelease")
                .withMethod("GET"))
            .respond(req -> {
                log.info("Received request to /blockUntilRelease");
                await()
                    .until(() -> !blockResponse.get());
                log.info("Received release for /blockUntilRelease");
                return response()
                    .withStatusCode(200);
            });
        httpGlueCode.sendEmptyRequestNonBlocking(Method.GET,
            "http://localhost:" + client.getPort() + "/blockUntilRelease");
        // this method returns BEFORE response is returned, so we now can release the response in the server
        log.info("Sent request to /blockUntilRelease, now unblocking");
        blockResponse.set(false);
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "GET");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/blockUntilRelease");
    }

    @Test
    void getRequestToFolder() {
        httpGlueCode.sendEmptyRequest(Method.GET, "http://winstone/target");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "GET");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/target\\/?");
    }

    // TODO these two methods do not really test anything besides correct construction of the Request
    // maybe rework with httpbin url as in complex put test method above
    @Test
    void putRequestToFolder() {
        httpGlueCode.sendEmptyRequest(Method.PUT, "http://winstone/target");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/target\\/?");
        tigerGlue.tgrAssertMatches("!{rbel:currentResponseAsString('$.responseCode')}", "405");
    }

    @Test
    void putRequestWithBodyToFolder() {
        httpGlueCode.sendRequestWithBody(Method.PUT, "http://winstone/target", "{'hello': 'world!'}");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/target\\/?");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.hello')}", "world!");
        tigerGlue.tgrAssertMatches("!{rbel:currentResponseAsString('$.responseCode')}", "405");
    }

    @Test
    void putRequestWithBodyFromFileToFolder() {
        httpGlueCode.sendRequestWithBody(Method.PUT,"http://winstone/target","!{file('pom.xml')}");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/target\\/?");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}" ,"4.0.0");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "application/xml.*");
    }

    @Test
    void putWithBodyAndSetContentType() {
        httpGlueCode.setDefaultHeader("Content-Type", "text/plain");
        httpGlueCode.sendRequestWithBody(Method.PUT, "http://winstone/target", "!{file('pom.xml')}");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "text/plain.*");
    }

    @Test
    void deleteRequestWithoutBody() {
        httpGlueCode.sendEmptyRequest(Method.DELETE, "http://winstone/not_a_file");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}", "DELETE");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}", "\\/not_a_file\\/?");
    }

    private static final DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
    private static final DataTable.TableConverter tableConverter = new DataTableTypeRegistryTableConverter(registry);

    @Test
    void sendRequestWithCustomHeader() {
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("schmoo", "lar"));
        data.add(List.of("foo", "bar"));
        httpGlueCode.sendEmptyRequestWithHeaders(Method.GET, "http://winstone/not_a_file", DataTable.create(data, tableConverter));
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.foo')}", "bar");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.schmoo')}", "lar");
    }

    @Test
    void sendRequestWithDefaultHeader() {
        httpGlueCode.setDefaultHeader("key", "value");
        httpGlueCode.sendEmptyRequest(Method.GET, "http://winstone/target/not_a_file");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}", "value");
        httpGlueCode.sendRequestWithBody(Method.POST, "http://winstone/target/not_a_file", "hello world");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}", "value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body')}", "hello world");
    }

    @Test
    void sendGetRequestWithCustomAndDefaultHeader() {
        httpGlueCode.setDefaultHeader("key", "value");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("foo", "bar"));
        httpGlueCode.sendEmptyRequestWithHeaders(Method.GET, "http://winstone/not_a_file", DataTable.create(data, tableConverter));
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}", "value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.foo')}", "bar");
    }

    @Test
    void sendPostRequestWithCustomAndDefaultHeader() {
        tigerGlue.ctxtISetLocalVariableTo("configured_state_value", "some_value");
        tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param");

        httpGlueCode.setDefaultHeader("key", "value");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("${configured_param_name}", "state", "redirect_uri"));
        data.add(List.of("client_id", "${configured_state_value}", "https://my.redirect"));
        httpGlueCode.sendRequestWithParams(Method.POST, "http://winstone/not_a_file", DataTable.create(data, tableConverter));

        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.state')}", "some_value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.my_cool_param')}", "client_id");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "application/x-www-form-urlencoded.*");
    }

    @Test
    void sendPostRequestWithCustomAndDefaultHeader2() {
        tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param2");

        httpGlueCode.setDefaultHeader("Content-Type", "application/json");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("${configured_param_name}"));
        data.add(List.of("client_id"));
        httpGlueCode.sendRequestWithParams(Method.POST, "http://winstone/not_a_file", DataTable.create(data, tableConverter));

        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body')}", "my_cool_param2=client_id");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}", "application/json");
    }

    @Test
    void putRequestWithTemplatedBody() {
        httpGlueCode.sendRequestWithBody(Method.PUT,"http://winstone/target","{\"tgrEncodeAs\":\"JWT\","
            + "\"header\":{\"alg\": \"BP256R1\",\"typ\": \"JWT\"},\"body\":{\"foo\":\"bar\"}, \"signature\":{\"verifiedUsing\":\"idpSig\"}}");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.signature.verifiedUsing')}" ,"puk_idpSig");
    }

    @Test
    void tgrPauseExecutionWithMessageAndErrorMessage() {
        assertThatThrownBy(() -> {
            tigerGlue.tgrPauseExecutionWithMessageAndErrorMessage("Test", "Error");
        }).isInstanceOf(TigerTestEnvException.class)
            .hasMessageContaining("The step 'TGR pause test run execution with message \"{}\" and message in case of error \"{}\"' is not supported outside the Workflow UI. Please check the manual for more information.");
    }


    @Test
    void sendRequestWithDefaultHeaders() {
        httpGlueCode.setDefaultHeaders("key1=valueA\nkey2=valueB\nkey3=value=value\n  spacedkey = value with spaces  ");
        httpGlueCode.sendEmptyRequest(Method.GET, "http://winstone/target/not_a_file");
        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key1')}", "valueA");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key2')}", "valueB");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key3')}", "value=value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.spacedkey')}", "value with spaces");
    }


}
