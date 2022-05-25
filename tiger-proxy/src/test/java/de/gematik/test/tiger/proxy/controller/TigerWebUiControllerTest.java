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

package de.gematik.test.tiger.proxy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.AbstractTigerProxyTest;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class TigerWebUiControllerTest extends AbstractTigerProxyTest {

    ConfigurableApplicationContext applicationContext;

    int adminPort;

    public void spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration configuration) {
        if (configuration == null) {
            configuration = TigerProxyConfiguration.builder().build();
        }

        try (ServerSocket  socket = new ServerSocket(0)){
            adminPort = socket.getLocalPort();
            configuration.setAdminPort(adminPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", configuration.getAdminPort());
        properties.putAll(TigerSerializationUtil.toMap(configuration, "tigerProxy"));
        applicationContext = new SpringApplicationBuilder()
            .properties(properties)
            .sources(TigerProxyApplication.class)
            .web(WebApplicationType.SERVLET)
            .initializers()
            .run();

        tigerProxy = applicationContext.getBean(TigerProxy.class);

        proxyRest = Unirest.spawnInstance();
        proxyRest.config()
            .proxy("localhost", tigerProxy.getProxyPort())
            .sslContext(tigerProxy.buildSslContext());
    }

    public String getWebUiUrl() {
        return "http://localhost:" + adminPort + "/webui";
    }

    @AfterEach
    public void stopSpringBootProxy() {
        applicationContext.close();
    }

    @Test
    public void checkHtmlIsReturned() throws InterruptedException {
        spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        proxyRest.post("http://backend/notFoobar").asJson();

        Response response = RestAssured.given().get(getWebUiUrl());

        response.then().statusCode(200);
        String responseStr = response.asString();
        assertThat(responseStr).contains("msglist");
    }

    @Test
    public void checkMsgIsReturned() throws InterruptedException {
        spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        proxyRest.post("http://backend/notFoobar").asJson();

        Response response = RestAssured.given().get(getWebUiUrl() + "/getMsgAfter");
        response.then().statusCode(200);

        JSONObject json = new JSONObject(response.asString());
        assertThat(json.getJSONArray("metaMsgList").length()).isEqualTo(2);
        assertThat(json.getJSONArray("metaMsgList").getJSONObject(0).getString("uuid")).isEqualTo("" + tigerProxy.getRbelMessages().get(0).getUuid());
        assertThat(json.getJSONArray("metaMsgList").getJSONObject(1).getString("uuid")).isEqualTo("" + tigerProxy.getRbelMessages().get(1).getUuid());
    }

    @Test
    public void checkOnlyOneMsgIsReturnedWithLastMsgUuidSupplied() throws InterruptedException {
        spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        proxyRest.post("http://backend/notFoobar").asJson();

        Response response = RestAssured.given().get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessages().get(0).getUuid());
        response.then().statusCode(200);

        JSONObject json = new JSONObject(response.asString());
        assertThat(json.getJSONArray("metaMsgList").length()).isEqualTo(1);
        assertThat(json.getJSONArray("metaMsgList").getJSONObject(0).getString("uuid")).isEqualTo("" + tigerProxy.getRbelMessages().get(1).getUuid());
    }

    @Test
    public void checkNoMsgIsReturnedIfNoneExistsAfterRequested() throws InterruptedException {
        spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        proxyRest.post("http://backend/notFoobar").asJson();

        Response response = RestAssured.given().get(getWebUiUrl() + "/getMsgAfter?lastMsgUuid=" + tigerProxy.getRbelMessages().get(1).getUuid());
        response.then().statusCode(200);

        JSONObject json = new JSONObject(response.asString());
        assertThat(json.getJSONArray("metaMsgList").length()).isEqualTo(0);
    }


    @Test
    public void checkNoMsgIsReturnedAfterReset() throws InterruptedException {
        spawnTigerProxyAsSpringBootApplicationWith(TigerProxyConfiguration.builder()
            .proxyRoutes(List.of(TigerRoute.builder()
                .from("http://backend")
                .to("http://localhost:" + fakeBackendServer.port())
                .build()))
            .build());
        proxyRest.post("http://backend/notFoobar").asJson();

        Response response = RestAssured.given().get(getWebUiUrl() + "/resetMsgs");
        response.then().statusCode(200);

        response = RestAssured.given().get(getWebUiUrl() + "/getMsgAfter");
        response.then().statusCode(200);

        JSONObject json = new JSONObject(response.asString());
        assertThat(json.getJSONArray("metaMsgList").length()).isEqualTo(0);
    }
}
