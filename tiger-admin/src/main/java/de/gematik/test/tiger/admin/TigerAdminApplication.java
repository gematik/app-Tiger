package de.gematik.test.tiger.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@SpringBootApplication
public class TigerAdminApplication {

    @Autowired
    private ThymeleafProperties properties;

    public static void main(String[] args) { //NOSONAR
        SpringApplication.run(TigerAdminApplication.class, args);
    }

    @Bean
    public ITemplateResolver defaultTemplateResolver() {
        AbstractConfigurableTemplateResolver resolver;
        String prefix = properties.getPrefix();
        if (prefix != null && prefix.startsWith("classpath:")) {
            resolver = new ClassLoaderTemplateResolver();
            prefix = prefix.substring("classpath:".length());
        } else {
            resolver = new FileTemplateResolver();
        }
        resolver.setPrefix(prefix);
        resolver.setCacheable(properties.isCache());
        resolver.setSuffix(properties.getSuffix());
        resolver.setTemplateMode(properties.getMode());
        resolver.setCharacterEncoding(properties.getEncoding().toString());
        return resolver;
    }
}
