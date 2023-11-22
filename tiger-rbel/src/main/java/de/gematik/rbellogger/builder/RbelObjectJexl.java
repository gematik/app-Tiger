package de.gematik.rbellogger.builder;

import de.gematik.test.tiger.common.jexl.TigerJexlExecutor;

public class RbelObjectJexl {

  private RbelObjectJexl() {}

  public static void initJexl(RbelBuilderManager builder) {
    TigerJexlExecutor.registerAdditionalNamespace("rbelObject", builder);
  }
}
