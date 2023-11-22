package de.gematik.test.tiger.testenvmgr.data;

import lombok.Value;

@Value(staticConstructor = "of")
public class TigerConfigurationPropertyDto {

  String key;
  String value;
  String source;
}
