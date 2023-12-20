/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package io.cucumber.core.plugin.report;

import io.cucumber.messages.types.Location;

/**
 * There are two Location classes used in the cucumber variables: io.cucumber.plugin.event.Location
 * and io.cucumber.messages.types.Location.
 *
 * <p>With this converter we can convert one type in the other.
 */
public class LocationConverter {
  public Location convertLocation(io.cucumber.plugin.event.Location location) {
    return new Location((long) location.getLine(), (long) location.getColumn());
  }
}
