package de.gematik.rbellogger.converter.initializers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RbelKeyFolderInitializerTest {

  @Test
  void initialiseKeyFolderInitializer() {
    assertThat(new RbelKeyFolderInitializer("tiger-common/"))
            .isNotNull();
  }
}
