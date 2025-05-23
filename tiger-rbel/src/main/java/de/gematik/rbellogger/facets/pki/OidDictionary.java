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
package de.gematik.rbellogger.facets.pki;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.asn1.RbelAsn1Converter;
import de.gematik.rbellogger.facets.asn1.RbelAsn1OidFacet;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import lombok.val;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class OidDictionary {
  private static final Map<String, String> OID_DICTIONARY = new HashMap<>();

  static {
    synchronized (OID_DICTIONARY) {
      try (InputStream is =
          RbelAsn1Converter.class.getClassLoader().getResourceAsStream("ASN1Dictionary.xml")) {
        if (is != null) {
          Document doc = Jsoup.parse(is, "UTF-8", "");
          for (Element element : doc.select("oid")) {
            String id = element.select("dot").get(0).text().trim();
            String name = element.select("desc").get(0).text();
            OID_DICTIONARY.put(id, name);
          }
        }
      } catch (Exception e) {
      }
    }
  }

  public static void buildAndAddAsn1OidFacet(RbelElement parentNode, String id) {
    val humanReadableName = OID_DICTIONARY.get(id);
    if (humanReadableName != null) {
      parentNode.addFacet(new RbelAsn1OidFacet(RbelElement.wrap(parentNode, humanReadableName)));
    }
  }

  public static RbelElement buildAndAddAsn1OidFacet(RbelElement parentNode) {
    parentNode
        .printValue()
        .map(OID_DICTIONARY::get)
        .ifPresent(
            name -> parentNode.addFacet(new RbelAsn1OidFacet(RbelElement.wrap(parentNode, name))));
    return parentNode;
  }
}
