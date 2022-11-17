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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.config.TigerProperties;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import java.io.IOException;
import java.util.Objects;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class TigerProxyApplication implements ServletContextListener {

    @Getter
    private final ApplicationConfiguration applicationConfiguration;
    private TigerProxy tigerProxy;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - which would not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");

        Resource resource = new ClassPathResource("/build.properties", TigerProxyApplication.class);
        try {
            TigerProperties tigerProperties = new TigerProperties(resource.getURL());
            log.info("Starting Tiger Proxy " + tigerProperties.getFullBuildVersion());
        } catch (IOException exception) {
            log.warn("Unable to detect build version!", exception);
        }
        new SpringApplicationBuilder()
            .bannerMode(Mode.OFF)
            .sources(TigerProxyApplication.class)
            .initializers()
            .run(args);
    }

    @Bean
    public TigerProxy tigerProxy() {
        tigerProxy = new TigerProxy(
            Objects.requireNonNullElseGet(applicationConfiguration,
                TigerProxyConfiguration::new));
        return tigerProxy;
    }

    @Bean
    public SimpleModule rbelElementDeserializer() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(RbelElement.class, new JsonSerializer<>() {
            @Override
            public void serialize(RbelElement value, JsonGenerator gen, SerializerProvider serializers)
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

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (tigerProxy != null) {
            tigerProxy.shutdown();
        }
    }

    @Bean
    public RbelHtmlRenderer rbelHtmlRenderer() {
        var renderer = new RbelHtmlRenderer();
        renderer.setMaximumEntitySizeInBytes(applicationConfiguration.getSkipDisplayWhenMessageLargerThanKb() * 1024);
        return renderer;
    }
}
