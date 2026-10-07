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
 */
package de.gematik.test.tiger.lib.pcap;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class PcapMergeQueue {

  private static final int MERGE_THREADS = 2;
  private static final PcapMergeQueue INSTANCE = new PcapMergeQueue();

  private final ExecutorService executor =
      Executors.newFixedThreadPool(
          MERGE_THREADS,
          runnable -> {
            Thread thread = new Thread(runnable, "pcap-merge");
            thread.setDaemon(true);
            return thread;
          });
  private final Set<CompletableFuture<Void>> pending = ConcurrentHashMap.newKeySet();

  public static PcapMergeQueue getInstance() {
    return INSTANCE;
  }

  public void submit(Runnable merge) {
    CompletableFuture<Void> future =
        CompletableFuture.runAsync(
            () -> {
              try {
                merge.run();
              } catch (RuntimeException e) {
                log.warn("Pcap merge failed: {}", e.getMessage());
                log.debug("Pcap merge error:", e);
              }
            },
            executor);
    pending.add(future);
    future.whenComplete((result, error) -> pending.remove(future));
  }

  public void awaitAll() {
    CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
  }
}
