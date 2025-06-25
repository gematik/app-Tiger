package de.gematik.test.tiger.common.data.config.tigerproxy;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class TigerProxyModifierDescription {
  String name;
  Map<String, String> parameters;
}
