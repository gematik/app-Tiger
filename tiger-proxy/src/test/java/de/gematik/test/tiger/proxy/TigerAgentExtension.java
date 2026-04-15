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
package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.TigerAgent;
import java.lang.instrument.Instrumentation;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * JUnit 5 extension that self-attaches the TigerAgent before any tests run in the suite. This
 * enables master-secret logging (and any other byte-buddy instrumentation) when tests are executed
 * directly from the IDE, without needing {@code -javaagent} on the JVM command line.
 *
 * <p>Maven surefire already adds {@code -javaagent} via its {@code argLine}; this extension is a
 * no-op in that case because {@link ByteBuddyAgent#install()} is idempotent when the agent is
 * already loaded.
 */
@Slf4j
public class TigerAgentExtension implements BeforeAllCallback {

  private static final Namespace NAMESPACE = Namespace.create(TigerAgentExtension.class);
  private static final String INSTALLED_KEY = "agentInstalled";

  @Override
  public void beforeAll(ExtensionContext context) {
    // Only install once per JVM – use the root context store as a global flag.
    context
        .getRoot()
        .getStore(NAMESPACE)
        .getOrComputeIfAbsent(
            INSTALLED_KEY,
            key -> {
              try {
                Instrumentation inst = ByteBuddyAgent.install();
                TigerAgent.premain(null, inst);
                log.info("TigerAgent self-attached successfully (IDE run)");
              } catch (Exception e) {
                log.warn(
                    "Could not self-attach TigerAgent – master-secret logging will be unavailable."
                        + " Add -javaagent to your run configuration if needed. Cause: {}",
                    e.getMessage());
              }
              return Boolean.TRUE;
            },
            Boolean.class);
  }
}
