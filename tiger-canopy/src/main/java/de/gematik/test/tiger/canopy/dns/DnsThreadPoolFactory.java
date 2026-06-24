/*
 *
 * Copyright 2021-2026 gematik GmbH
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
 *
 */
package de.gematik.test.tiger.canopy.dns;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Spring Boot configuration for DNS thread pools. */
@Configuration
class DnsThreadPoolFactory {

  private static final int WORKER_POOL_SIZE = 16;
  private static final int WORKER_QUEUE_SIZE = 256;

  @Bean
  ThreadPoolTaskExecutor udpDnsWorkerPool() {
    return createBoundedPool("canopy-dns-udp-worker");
  }

  @Bean
  ThreadPoolTaskExecutor tcpDnsWorkerPool() {
    return createBoundedPool("canopy-dns-tcp-worker");
  }

  private static ThreadPoolTaskExecutor createBoundedPool(String namePrefix) {
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(WORKER_POOL_SIZE);
    pool.setMaxPoolSize(WORKER_POOL_SIZE);
    pool.setQueueCapacity(WORKER_QUEUE_SIZE);
    pool.setThreadNamePrefix(namePrefix + "-");
    pool.setDaemon(true);
    pool.setRejectedExecutionHandler(
        new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    pool.initialize();
    return pool;
  }
}
