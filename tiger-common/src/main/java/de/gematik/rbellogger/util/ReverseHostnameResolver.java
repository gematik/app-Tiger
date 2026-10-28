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
package de.gematik.rbellogger.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Names an IP address via reverse DNS, e.g. the peers of a recorded connection inside a Docker
 * network. Only for naming parsed traffic, never for routing (see ADR 024). Results are cached,
 * failures included. A lookup that exceeds the timeout yields no name for now, but still fills the
 * cache.
 *
 * <p>The names come from the resolver of the machine Tiger runs on: container names are only known
 * to Docker's DNS, i.e. if Tiger itself runs in the same Docker network. On the Docker host, peers
 * stay IPs unless the host's DNS knows them.
 */
@Slf4j
public class ReverseHostnameResolver {

  /** Resolves an address to its name, e.g. via a PTR record. */
  @FunctionalInterface
  public interface ReverseLookup {
    Optional<String> lookup(InetAddress address) throws UnknownHostException;
  }

  /** Resolves a name to its addresses. */
  @FunctionalInterface
  public interface ForwardLookup {
    List<InetAddress> lookup(String name) throws UnknownHostException;
  }

  /** The system resolver, i.e. Docker's DNS inside a container. */
  public static final ReverseLookup SYSTEM_REVERSE_LOOKUP =
      address -> {
        final String name = InetAddress.getByAddress(address.getAddress()).getHostName();
        return Optional.of(name).filter(n -> !InetAddresses.isInetAddress(n));
      };

  public static final ForwardLookup SYSTEM_FORWARD_LOOKUP =
      name -> Arrays.asList(InetAddress.getAllByName(name));

  private final ReverseLookup reverseLookup;
  private final ForwardLookup forwardLookup;
  private final Duration timeout;
  private final Cache<InetAddress, CompletableFuture<Optional<String>>> cache;
  private final ExecutorService executor =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("tiger-reverse-dns-%d").setDaemon(true).build());

  public ReverseHostnameResolver(
      ReverseLookup reverseLookup,
      ForwardLookup forwardLookup,
      Duration cacheTtl,
      Duration timeout) {
    this.reverseLookup = reverseLookup;
    this.forwardLookup = forwardLookup;
    this.timeout = timeout;
    this.cache = CacheBuilder.newBuilder().expireAfterWrite(cacheTtl).maximumSize(10_000).build();
  }

  /**
   * Uses the system resolver, caches for {@code tiger.rbel.dnsCacheTtlSeconds} and waits at most
   * {@code tiger.rbel.reverseDnsLookupTimeoutMs}.
   */
  public static ReverseHostnameResolver fromConfiguration() {
    return new ReverseHostnameResolver(
        SYSTEM_REVERSE_LOOKUP,
        SYSTEM_FORWARD_LOOKUP,
        Duration.ofSeconds(
            Math.max(0, TigerConfigurationKeys.RBEL_DNS_CACHE_TTL_SECONDS.getValueOrDefault())),
        Duration.ofMillis(
            Math.max(
                0, TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP_TIMEOUT_MS.getValueOrDefault())));
  }

  public static boolean isActive() {
    return TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP.getValueOrDefault();
  }

  /**
   * The name the address resolves to. Docker answers with {@code <container>.<network>}: if the
   * first label alone resolves back to the address, only that label is returned, otherwise the full
   * name (e.g. {@code dev.mydomain.com} stays as it is).
   */
  public Optional<String> lookup(InetAddress address) {
    try {
      return cache
          .get(address, () -> CompletableFuture.supplyAsync(() -> lookupSafely(address), executor))
          .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      log.atDebug().addArgument(address).log("Reverse DNS lookup for {} timed out");
      return Optional.empty();
    } catch (InterruptedException e) {
      log.atTrace().addArgument(address).log("Reverse DNS lookup for {} was interrupted");
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (ExecutionException e) {
      log.atTrace().addArgument(address).setCause(e).log("Reverse DNS lookup for {} failed");
      return Optional.empty();
    }
  }

  private Optional<String> lookupSafely(InetAddress address) {
    try {
      return reverseLookup
          .lookup(address)
          .map(name -> name.endsWith(".") ? name.substring(0, name.length() - 1) : name)
          .filter(name -> !name.isBlank())
          .map(name -> shortenIfUnambiguous(name, address));
    } catch (UnknownHostException | RuntimeException e) {
      log.atDebug().addArgument(address).setCause(e).log("Reverse DNS lookup for {} failed");
      return Optional.empty();
    }
  }

  private String shortenIfUnambiguous(String name, InetAddress address) {
    final int firstDot = name.indexOf('.');
    if (firstDot <= 0) {
      return name;
    }
    final String firstLabel = name.substring(0, firstDot);
    try {
      return forwardLookup.lookup(firstLabel).contains(address) ? firstLabel : name;
    } catch (UnknownHostException | RuntimeException e) {
      log.atTrace()
          .addArgument(firstLabel)
          .setCause(e)
          .log("{} does not resolve on its own, keeping the full name");
      return name;
    }
  }
}
