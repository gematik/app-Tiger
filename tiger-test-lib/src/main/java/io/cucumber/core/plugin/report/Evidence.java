package io.cucumber.core.plugin.report;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Evidence {

  public Evidence(Type type, String title) {
    this(type, title, null);
  }

  public enum Type {
    INFO,
    WARN,
    ERROR,
    FATAL
  }

  Type type;
  String title;

  Object details;
}
