/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.httpclient;

import de.gematik.test.tiger.glue.HttpGlueCode;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.glue.TigerGlue;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.restassured.http.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Performs all tests as defined in HttpGlueCodeTest.feature as unit tests so that the coverage of the glue code is also added to sonar.
 * Cucumber does some bytebuddy instrumentation magic with the glue code so by running the feature file as test, no coverage is reported for the glue code.
 */
public class TestHttpClientSteps {

    HttpGlueCode httpGlueCode = new HttpGlueCode();
    RBelValidatorGlue rbelValidatorGlueCode = new RBelValidatorGlue();
    TigerGlue tigerGlue = new TigerGlue();

    @BeforeAll
    public static void resetTiger() {
        TigerDirector.testUninitialize();
    }

    @BeforeEach
    public synchronized void clearMessages() {
        if (!TigerDirector.isInitialized()) {
            System.setProperty("tiger.testenv.cfgfile", "src/test/resources/testdata/httpclientenv.yaml");
            TigerDirector.start();
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
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","GET");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/?");
    }

    @Test
    void getRequestToFolder() {
        httpGlueCode.sendEmptyRequest(Method.GET,"http://winstone/target");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","GET");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/target\\/?");
    }

    @Test
    void putRequestToFolder() {
        httpGlueCode.sendEmptyRequest(Method.PUT,"http://winstone/target");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/target\\/?");
    }

    @Test
    void putRequestWithBodyToFolder() {
        httpGlueCode.sendRequestWithBody(Method.PUT,"{'hello': 'world!'}","http://winstone/target");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/target\\/?");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.hello')}" ,"world!");
    }

    @Test
    void putRequestWithBodyFromFileToFolder() {
        httpGlueCode.sendRequestWithBody(Method.PUT,"!{file('pom.xml')}","http://winstone/target");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","PUT");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/target\\/?");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}" ,"4.0.0");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}" ,"text/plain.*");
    }


    @Test
    void deleteRequestWithoutBody() {
        httpGlueCode.sendEmptyRequest(Method.DELETE,"http://winstone/not_a_file");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.method')}","DELETE");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.path')}" ,"\\/not_a_file\\/?");
    }
    private static final DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
    private static final DataTable.TableConverter tableConverter = new DataTableTypeRegistryTableConverter(registry);

    @Test
    void sendRequestWithCustomHeader() {
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("schmoo", "lar"));
        data.add(List.of("foo", "bar"));
        httpGlueCode.sendEmptyRequestWithHeaders(Method.GET,"http://winstone/not_a_file", DataTable.create(data, tableConverter));
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.foo')}" ,"bar");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.schmoo')}" ,"lar");
    }

    @Test
    void sendRequestWithDefaultHeader() {
        httpGlueCode.addDefaultHeader("key", "value");
        httpGlueCode.sendEmptyRequest(Method.GET,"http://winstone/target/not_a_file");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}","value");
        httpGlueCode.sendRequestWithBody(Method.POST, "hello world", "http://winstone/target/not_a_file");
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}","value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body')}","hello world");
    }

    @Test
    void sendGetRequestWithCustomAndDefaultHeader() {
        httpGlueCode.addDefaultHeader("key", "value");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("foo", "bar"));
        httpGlueCode.sendEmptyRequestWithHeaders(Method.GET,"http://winstone/not_a_file", DataTable.create(data, tableConverter));
        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.key')}","value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.foo')}","bar");
    }

    @Test
    void sendPostRequestWithCustomAndDefaultHeader() {
        tigerGlue.ctxtISetLocalVariableTo("configured_state_value","some_value");
        tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param");

        httpGlueCode.addDefaultHeader("key", "value");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("${configured_param_name}", "state", "redirect_uri"));
        data.add(List.of("client_id", "${configured_state_value}", "https://my.redirect"));
        httpGlueCode.sendPostRequestToWith(Method.POST,"http://winstone/not_a_file", DataTable.create(data, tableConverter));

        rbelValidatorGlueCode. findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.state')}","some_value");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body.my_cool_param')}","client_id");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}","application/x-www-form-urlencoded.*");
    }

    @Test
    void sendPostRequestWithCustomAndDefaultHeader2() {
        tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param2");

        httpGlueCode.addDefaultHeader("Content-Type", "application/json");
        List<List<String>> data = new ArrayList<>();
        data.add(List.of("${configured_param_name}"));
        data.add(List.of( "client_id"));
        httpGlueCode.sendPostRequestToWith(Method.POST,"http://winstone/not_a_file", DataTable.create(data, tableConverter));

        rbelValidatorGlueCode.findLastRequestToPath(".*");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.body')}","my_cool_param2=client_id");
        tigerGlue.tgrAssertMatches("!{rbel:currentRequestAsString('$.header.Content-Type')}","application/json");
    }
}
