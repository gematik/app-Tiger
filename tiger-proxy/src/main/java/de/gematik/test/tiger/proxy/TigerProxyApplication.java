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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.server.TigerBuildPropertiesService;
import jakarta.servlet.ServletContextListener;
import java.io.IOException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(
    scanBasePackageClasses = {TigerBuildPropertiesService.class, TigerProxyApplication.class})
@RequiredArgsConstructor
@Slf4j
public class TigerProxyApplication implements ServletContextListener {

  @Getter private final TigerProxyConfiguration proxyConfiguration;

  public static void main(String[] args) { // NOSONAR
    // Necessary hack to avoid mockserver activating java.util.logging - which would not work in
    // combination
    // with spring boot!
    System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");

    new SpringApplicationBuilder()
        .bannerMode(Mode.OFF)
        .sources(TigerProxyApplication.class)
        .initializers()
        .run(args);
  }

  @Bean
  public SimpleModule rbelElementDeserializer() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(
        RbelElement.class,
        new JsonSerializer<>() {
          @Override
          public void serialize(
              RbelElement value, JsonGenerator gen, SerializerProvider serializers)
              throws IOException {
            gen.writeStartObject();
            gen.writeStringField("uuid", value.getUuid());
            gen.writeArrayFieldStart("facets");
            for (RbelFacet facet : value.getFacets()) {
              gen.writeString(facet.getClass().getSimpleName());
            }
            gen.writeEndArray();
            gen.writeEndObject();
          }
        });
    return module;
  }

  @Bean
  public RbelHtmlRenderer rbelHtmlRenderer() {
    var renderer = new RbelHtmlRenderer();
    renderer.setMaximumEntitySizeInBytes(
        proxyConfiguration.getSkipDisplayWhenMessageLargerThanKb() * 1024);
    return renderer;
  }
}
