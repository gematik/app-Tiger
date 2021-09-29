/*
 * Copyright (c) 2021 gematik GmbH
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

import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerStandaloneProxyApplication {

    @Getter
    private final ApplicationConfiguration applicationConfiguration;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - which would not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");
        SpringApplication.run(TigerStandaloneProxyApplication.class, args);
    }

    @Bean
    public TigerProxy tigerProxy() {
        if (applicationConfiguration != null) {
            return new TigerProxy(applicationConfiguration);
        } else {
            return new TigerProxy(new TigerProxyConfiguration());
        }
    }
}
