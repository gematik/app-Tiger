/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.admin;

import de.gematik.test.tiger.spring_utils.TigerBuildPropertiesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafProperties;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@SpringBootApplication(scanBasePackageClasses = {TigerBuildPropertiesService.class, TigerAdminApplication.class})
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
