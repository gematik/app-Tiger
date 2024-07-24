package de.gematik.test.tiger;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

//NOSONAR
public class TigerAgent {

  public static TigerMasterSecretListeners listener = s-> {};

  public static void main(String[] args) {
    System.err.println("This is a Java Agent and should be attached to a running JVM process.");
  }

  public static void premain(String args, Instrumentation inst) {
    System.out.println("TigerAgent premain method invoked! 🐯");

    new AgentBuilder.Default()
      .ignore(ElementMatchers.none())
      .type(ElementMatchers.named("org.bouncycastle.tls.AbstractTlsContext"))
      .transform((builder, typeDescription, classLoader, module, o) -> builder
        .visit(Advice.to(HandshakeCompleteInterceptor.class).on(ElementMatchers.hasMethodName("handshakeComplete"))))
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
