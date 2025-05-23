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
