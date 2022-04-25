/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import java.io.IOException;
import java.util.Objects;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerProxyApplication implements ServletContextListener {

    @Getter
    private final ApplicationConfiguration applicationConfiguration;
    private TigerProxy tigerProxy;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - which would not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");
        SpringApplication.run(TigerProxyApplication.class, args);
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
        tigerProxy.shutdown();
    }
}
