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
package de.gematik.rbellogger.facets.ldap;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class RbelLdapControlsFacet implements RbelFacet {

  private final RbelMultiMap<RbelElement> controls = new RbelMultiMap<>();
  private final List<ControlEntry> entries = new ArrayList<>();

  public void put(String oid, RbelElement element) {
    String key = sanitize(oid);
    controls.put(key, element);
    entries.add(new ControlEntry(oid, key, element));
  }

  public List<ControlEntry> entries() {
    return Collections.unmodifiableList(entries);
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return controls;
  }

  public static String sanitize(String oid) {
    return "oid_" + oid.replaceAll("[^A-Za-z0-9_]", "_");
  }

  public record ControlEntry(String oid, String key, RbelElement element) {}
}
