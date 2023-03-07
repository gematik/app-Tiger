package io.cucumber.core.plugin;

import lombok.experimental.Delegate;

public class ScenarioContextDelegate {

  public ScenarioContextDelegate(ScenarioContext context) {
    this.context = context;
  }

  @Delegate
  private final ScenarioContext context;
}
