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
package de.gematik.test.tiger.lib.httpclient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.test.tiger.glue.TigerParameterTypeDefinitions.tigerResolvedString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.HttpGlueCode;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.glue.TigerGlue;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerHttpClient;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableTypeRegistry;
import io.cucumber.datatable.DataTableTypeRegistryTableConverter;
import io.restassured.http.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Performs all tests as defined in HttpGlueCodeTest.feature as unit tests so that the coverage of
 * the glue code is also added to sonar. Cucumber does some bytebuddy instrumentation magic with the
 * glue code so by running the feature file as test, no coverage is reported for the glue code.
 */
@Slf4j
class TestHttpClientSteps {

  private HttpGlueCode httpGlueCode;
  private RBelValidatorGlue rbelValidatorGlueCode;
  private TigerGlue tigerGlue;

  @BeforeAll
  static void resetTiger() {
    TigerHttpClient.reset();
    TigerDirector.testUninitialize();
  }

  @BeforeEach
  synchronized void clearMessages() {
    if (!TigerDirector.isInitialized()) {
      System.setProperty("tiger.testenv.cfgfile", "tiger.yaml");
      TigerDirector.start();
      TigerDirector.getLibConfig().getHttpClientConfig().setActivateRbelWriter(true);
    }
    httpGlueCode = new HttpGlueCode();
    rbelValidatorGlueCode = new RBelValidatorGlue();
    tigerGlue = new TigerGlue();
    rbelValidatorGlueCode.tgrClearRecordedMessages();
  }

  @AfterEach
  void clearDefaultHeaders() {
    httpGlueCode.clearDefaultHeaders();
  }

  @AfterAll
  static void shutdown() {
    TigerTestEnvMgr tigerTestEnvMgr = TigerDirector.getTigerTestEnvMgr();
    if (tigerTestEnvMgr.isShuttingDown()) {
      return;
    }
    tigerTestEnvMgr.shutDown();
    System.clearProperty("tiger.testenv.cfgfile");
    TigerGlobalConfiguration.reset();
    TigerHttpClient.reset();
    TigerDirector.getLibConfig().getHttpClientConfig().setActivateRbelWriter(false);
  }

  @Test
  void simpleGetRequest() { // NOSONAR
    httpGlueCode.sendEmptyRequest(Method.GET, createAddress("http://httpbin/"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        TigerParameterTypeDefinitions.tigerResolvedString(
            "!{rbel:currentRequestAsString('$.method')}"),
        "GET");
    tigerGlue.tgrAssertMatches(
        TigerParameterTypeDefinitions.tigerResolvedString(
            "!{rbel:currentRequestAsString('$.path')}"),
        "\\/?");
  }

  @Test
  void sendComplexPost() { // NOSONAR
    httpGlueCode.sendRequestWithMultiLineBody(
        Method.POST,
        createAddress("http://httpbin/post"),
        """
        {
          "object": { "field": "value" },
          "array" : [ "1", 2, { "field2": "value2" } ],
          "member" : "test"
        }
        """);
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "POST");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/json; charset=UTF-8");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.object.field')}"), "value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentResponseAsString('$.responseCode')}"), "200");
  }

  @Test
  void sendComplexPostMultiLine() { // NOSONAR
    httpGlueCode.sendRequestWithMultiLineBody(
        Method.POST,
        createAddress("http://httpbin/post"),
        "application/json",
        """
        {
          "object": { "field": "value" },
          "array" : [ "1", 2, { "field2": "value2" } ],
          "member" : "test"
        }
        """);
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "POST");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/json");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.object.field')}"), "value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentResponseAsString('$.responseCode')}"), "200");
  }

  @Test
  void sendComplexPut() { // NOSONAR
    httpGlueCode.sendRequestWithMultiLineBody(
        Method.PUT,
        createAddress("http://httpbin/put"),
        """
        {
          "object": { "field": "value" },
          "array" : [ "1", 2, { "field2": "value2" } ],
          "member" : "test"
        }
        """);
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "PUT");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/json; charset=UTF-8");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.object.field')}"), "value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentResponseAsString('$.responseCode')}"), "200");
  }

  @Test
  void simpleGetRequestNonBlocking() {
    AtomicBoolean blockResponse = new AtomicBoolean(true);

    WireMockServer server =
        new WireMockServer(
            WireMockConfiguration.options()
                .dynamicPort()
                .extensions(new CustomBlockingTransformer(blockResponse)));

    try {
      server.start();
      server.stubFor(
          get(urlEqualTo("/blockUntilRelease"))
              .willReturn(aResponse().withTransformers("block-until-atomic").withStatus(200)));
      httpGlueCode.sendEmptyRequestNonBlocking(
          Method.GET, createAddress("http://localhost:" + server.port() + "/blockUntilRelease"));

      log.info("Sent request to /blockUntilRelease, now unblocking");
      blockResponse.set(false);
      rbelValidatorGlueCode.findLastRequestToPath(".*");
      rbelValidatorGlueCode.currentResponseMessageAttributeMatches("$.responseCode", "200");
      tigerGlue.tgrAssertMatches(
          tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "GET");
      tigerGlue.tgrAssertMatches(
          tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/blockUntilRelease");
    } finally {
      server.stop();
    }
  }

  @Test
  void getRequestToFolder() { // NOSONAR
    httpGlueCode.sendEmptyRequest(Method.GET, createAddress("http://httpbin/get"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "GET");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/get\\/?");
  }

  @Test
  void putRequestToFolder() { // NOSONAR
    httpGlueCode.sendEmptyRequest(Method.PUT, createAddress("http://httpbin/put"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "PUT");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/put\\/?");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentResponseAsString('$.responseCode')}"), "200");
  }

  @Test
  void putRequestWithBodyToFolder() { // NOSONAR
    httpGlueCode.sendRequestWithBody(
        Method.PUT, createAddress("http://httpbin/put"), "{'hello': 'world!'}");
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "PUT");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/put\\/?");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.hello')}"), "world!");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentResponseAsString('$.responseCode')}"), "200");
  }

  @Test
  void putRequestWithBodyFromFileToFolder() { // NOSONAR
    httpGlueCode.sendRequestWithBody(
        Method.PUT, createAddress("http://httpbin/put"), "!{file('pom.xml')}");
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "PUT");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/put\\/?");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.project.modelVersion.text')}"),
        "4.0.0");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/xml.*");
  }

  @Test
  void putWithBodyAndSetContentType() { // NOSONAR
    httpGlueCode.setDefaultHeader("Content-Type", "text/plain");
    httpGlueCode.sendRequestWithBody(
        Method.PUT, createAddress("http://httpbin/put"), "!{file('pom.xml')}");
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "text/plain");
  }

  @Test
  void deleteRequestWithoutBody() { // NOSONAR
    httpGlueCode.sendEmptyRequest(Method.DELETE, createAddress("http://httpbin/delete"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.method')}"), "DELETE");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.path')}"), "\\/delete\\/?");
  }

  private static final DataTableTypeRegistry registry = new DataTableTypeRegistry(Locale.ENGLISH);
  private static final DataTable.TableConverter tableConverter =
      new DataTableTypeRegistryTableConverter(registry);

  @Test
  void sendRequestWithCustomHeader() { // NOSONAR
    List<List<String>> data = new ArrayList<>();
    data.add(List.of("schmoo", "lar"));
    data.add(List.of("foo", "bar"));
    httpGlueCode.sendEmptyRequestWithHeaders(
        Method.GET, createAddress("http://httpbin/get"), DataTable.create(data, tableConverter));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.foo')}"), "bar");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.schmoo')}"), "lar");
  }

  @Test
  void sendRequestWithDefaultHeader() { // NOSONAR
    httpGlueCode.setDefaultHeader("key", "value");
    httpGlueCode.sendEmptyRequest(Method.GET, createAddress("http://httpbin/get"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key')}"), "value");
    httpGlueCode.sendRequestWithBody(
        Method.POST, createAddress("http://httpbin/post"), "hello world");
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key')}"), "value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body')}"), "hello world");
  }

  @Test
  void sendGetRequestWithCustomAndDefaultHeader() { // NOSONAR
    httpGlueCode.setDefaultHeader("key", "value");
    List<List<String>> data = new ArrayList<>();
    data.add(List.of("foo", "bar"));
    httpGlueCode.sendEmptyRequestWithHeaders(
        Method.GET, createAddress("http://httpbin/get"), DataTable.create(data, tableConverter));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key')}"), "value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.foo')}"), "bar");
  }

  @Test
  void sendPostRequestWithCustomAndDefaultHeader() { // NOSONAR
    tigerGlue.ctxtISetLocalVariableTo("configured_state_value", "some_value");
    tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param");

    httpGlueCode.setDefaultHeader("key", "value");
    List<List<String>> data = new ArrayList<>();
    data.add(List.of("${configured_param_name}", "state", "redirect_uri"));
    data.add(List.of("client_id", "${configured_state_value}", "https://my.redirect"));
    httpGlueCode.sendRequestWithParams(
        Method.POST, createAddress("http://httpbin/post"), DataTable.create(data, tableConverter));

    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.state')}"), "some_value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.my_cool_param')}"), "client_id");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/x-www-form-urlencoded.*");
  }

  @Test
  void sendPostRequestWithCustomAndDefaultHeader2() { // NOSONAR
    tigerGlue.ctxtISetLocalVariableTo("configured_param_name", "my_cool_param2");

    httpGlueCode.setDefaultHeader("Content-Type", "application/json");
    List<List<String>> data = new ArrayList<>();
    data.add(List.of("${configured_param_name}"));
    data.add(List.of("client_id"));
    httpGlueCode.sendRequestWithParams(
        Method.POST, createAddress("http://httpbin/post"), DataTable.create(data, tableConverter));

    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body')}"),
        "my_cool_param2=client_id");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        "application/json");
  }

  @Test
  void putRequestWithTemplatedBody() { // NOSONAR
    httpGlueCode.sendRequestWithBody(
        Method.PUT,
        createAddress("http://httpbin/put"),
        "{\"tgrEncodeAs\":\"JWT\",\"header\":{\"alg\": \"BP256R1\",\"typ\":"
            + " \"JWT\"},\"body\":{\"foo\":\"bar\"},"
            + " \"signature\":{\"verifiedUsing\":\"idpSig\"}}");
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.body.signature.verifiedUsing')}"),
        "puk_idpSig");
  }

  @Test
  void tgrPauseExecutionWithMessageAndErrorMessage() {
    assertThatThrownBy(
            () -> {
              tigerGlue.tgrPauseExecutionWithMessageAndErrorMessage("Test", "Error");
            })
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining(
            "The step 'TGR pause test run execution with message \"{}\" and message in case of"
                + " error \"{}\"' is not supported outside the Workflow UI. Please check the manual"
                + " for more information.");
  }

  @Test
  void sendRequestWithDefaultHeaders() { // NOSONAR
    httpGlueCode.setDefaultHeaders(
        "key1=valueA\nkey2=valueB\nkey3=value=value\n  spacedkey = value with spaces  ");
    httpGlueCode.sendEmptyRequest(Method.GET, createAddress("http://httpbin/get/"));
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key1')}"), "valueA");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key2')}"), "valueB");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.key3')}"), "value=value");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.spacedkey')}"),
        "value with spaces");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
          {"hello": "world"}, application/json; charset=UTF-8
          <this><is>xml</is></this>, application/xml; charset=ISO-8859-1
          just some text, application/octet-stream; charset=ISO-8859-1
          """)
  void
      sendRequestWithoutExplicitHeaders_shouldAutomaticallyAddContentTypeAndCharsetBasedOnContent( // NOSONAR
      String bodyContent, String expectedContentTypeHeader) {
    httpGlueCode.sendRequestWithBody(
        Method.POST, createAddress("http://httpbin/headers"), bodyContent);
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        expectedContentTypeHeader);
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
              {"hello": "world"}, application/my-own-thing
              <this><is>xml</is></this>, application/fancy-xml
              just some text, application/text
              {"hello": "world"}, application/json; charset=ISO-8859-1
              <this><is>xml</is></this>, application/xml; charset=UTF-8
              just some text, application/text; charset=UTF-8
          """)
  void sendRequestWithExplicitHeaders_shouldNotAddAnythingAutomatically( // NOSONAR
      String bodyContent, String explicitSetContentTypeHeader) {
    httpGlueCode.setDefaultHeader("Content-Type", explicitSetContentTypeHeader);
    httpGlueCode.sendRequestWithBody(
        Method.POST, createAddress("http://httpbin/headers"), bodyContent);
    rbelValidatorGlueCode.findLastRequestToPath(".*");
    tigerGlue.tgrAssertMatches(
        tigerResolvedString("!{rbel:currentRequestAsString('$.header.Content-Type')}"),
        explicitSetContentTypeHeader);
  }

  private static URI createAddress(String urlString) {
    return URI.create(urlString);
  }

  class CustomBlockingTransformer extends ResponseDefinitionTransformer {
    private final AtomicBoolean blockResponse;

    public CustomBlockingTransformer(AtomicBoolean blockResponse) {
      this.blockResponse = blockResponse;
    }

    @Override
    public ResponseDefinition transform(
        Request request,
        ResponseDefinition responseDefinition,
        FileSource files,
        Parameters parameters) {
      await().until(() -> !blockResponse.get());

      return ResponseDefinitionBuilder.like(responseDefinition).build();
    }

    @Override
    public String getName() {
      return "block-until-atomic";
    }

    @Override
    public boolean applyGlobally() {
      return false;
    }
  }
}
