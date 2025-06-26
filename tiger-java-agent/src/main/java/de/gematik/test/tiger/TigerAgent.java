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
package de.gematik.test.tiger;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

// NOSONAR
public class TigerAgent {

  public static TigerMasterSecretListeners listener = s -> {};

  public static void main(String[] args) {
    System.err.println("This is a Java Agent and should be attached to a running JVM process.");
  }

  public static void premain(String args, Instrumentation inst) {
    System.out.println("Tiger Agent loaded! TLS master secrets can now be stored to file 🐯");

    new AgentBuilder.Default()
        .ignore(ElementMatchers.none())
        .type(ElementMatchers.named("org.bouncycastle.tls.AbstractTlsContext"))
        .transform(
            (builder, typeDescription, classLoader, module, o) ->
                builder.visit(
                    Advice.to(HandshakeCompleteInterceptor.class)
                        .on(ElementMatchers.hasMethodName("handshakeComplete"))))
        .installOn(inst);
  }

  public static class HandshakeCompleteInterceptor {
    @Advice.OnMethodExit
    public static void onExit(@Advice.This Object tlsContext) {
      listener.onMasterSecret(tlsContext);
    }
  }

  public static void addListener(TigerMasterSecretListeners listener) {
    TigerAgent.listener = listener;
  }
}
