/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerProxyApplication {

    @Getter
    private final ApplicationConfiguration applicationConfiguration;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - which would not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");
        SpringApplication.run(TigerProxyApplication.class, args);
    }

    @Bean
    public TigerProxy tigerProxy() {
        if (applicationConfiguration != null) {
            return new TigerProxy(applicationConfiguration);
        } else {
            return new TigerProxy(new TigerProxyConfiguration());
        }
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
}
