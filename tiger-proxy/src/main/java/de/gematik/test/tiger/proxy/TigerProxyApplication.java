/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import java.io.IOException;
import javax.servlet.ServletContextListener;
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

  @Getter private final ApplicationConfiguration applicationConfiguration;

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
        applicationConfiguration.getSkipDisplayWhenMessageLargerThanKb() * 1024);
    return renderer;
  }
}
