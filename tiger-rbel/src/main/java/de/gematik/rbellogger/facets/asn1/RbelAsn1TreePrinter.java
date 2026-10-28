/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.asn1;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bouncycastle.util.encoders.Hex;

/**
 * Prints an ASN.1 tree one data object per line: its RbelPath key, its type and, for primitive data
 * objects, its value. Long values are abbreviated.
 */
final class RbelAsn1TreePrinter {

  record Line(int depth, String key, String type, String value) {}

  private static final int MAX_VALUE_LENGTH = 64;
  private static final int ABBREVIATED_HEAD_LENGTH = 32;
  private static final int ABBREVIATED_TAIL_LENGTH = 8;
  private static final String[] TAG_CLASSES = {"UNIVERSAL", "APPLICATION", "CONTEXT", "PRIVATE"};
  private static final Map<Integer, String> UNIVERSAL_TYPES =
      Map.ofEntries(
          Map.entry(0x01, "BOOLEAN"),
          Map.entry(0x02, "INTEGER"),
          Map.entry(0x03, "BIT STRING"),
          Map.entry(0x04, "OCTET STRING"),
          Map.entry(0x05, "NULL"),
          Map.entry(0x06, "OBJECT IDENTIFIER"),
          Map.entry(0x0A, "ENUMERATED"),
          Map.entry(0x0C, "UTF8String"),
          Map.entry(0x12, "NumericString"),
          Map.entry(0x13, "PrintableString"),
          Map.entry(0x14, "T61String"),
          Map.entry(0x16, "IA5String"),
          Map.entry(0x17, "UTCTime"),
          Map.entry(0x18, "GeneralizedTime"),
          Map.entry(0x1A, "VisibleString"),
          Map.entry(0x1E, "BMPString"),
          Map.entry(0x30, "SEQUENCE"),
          Map.entry(0x31, "SET"));

  private RbelAsn1TreePrinter() {}

  static List<Line> print(RbelElement element) {
    final List<Line> lines = new ArrayList<>();
    final RbelAsn1Facet facet = element.getFacetOrFail(RbelAsn1Facet.class);
    if (facet.getTag() == null) {
      printChildren(facet, 0, lines);
    } else {
      print("", element, 0, lines);
    }
    return lines;
  }

  private static void print(String key, RbelElement element, int depth, List<Line> lines) {
    final RbelAsn1Facet facet = element.getFacetOrFail(RbelAsn1Facet.class);
    lines.add(new Line(depth, key, describeTag(facet.getTag()), printValue(element, facet)));
    printChildren(facet, depth + 1, lines);
  }

  private static void printChildren(RbelAsn1Facet facet, int depth, List<Line> lines) {
    for (Entry<String, RbelElement> child : facet.getChildElements().entries()) {
      if (child.getValue().hasFacet(RbelAsn1Facet.class)) {
        print(child.getKey(), child.getValue(), depth, lines);
      }
    }
  }

  static String describeTag(String tagHex) {
    final byte[] tagBytes = Hex.decode(tagHex);
    final int tagClass = (tagBytes[0] & 0xC0) >> 6;
    int number = tagBytes[0] & 0x1F;
    if (tagBytes.length > 1) {
      number = 0;
      for (int i = 1; i < tagBytes.length; i++) {
        number = (number << 7) | (tagBytes[i] & 0x7F);
      }
    }
    if (tagClass == 0) {
      return UNIVERSAL_TYPES.getOrDefault(
          Byte.toUnsignedInt(tagBytes[0]), "[UNIVERSAL " + number + "]");
    }
    return "[" + TAG_CLASSES[tagClass] + " " + number + "]";
  }

  private static String printValue(RbelElement element, RbelAsn1Facet facet) {
    if (!facet.getChildElements().isEmpty()) {
      return "";
    }
    final Object value =
        element.getFacet(RbelValueFacet.class).map(RbelValueFacet::getValue).orElse(null);
    if (value == null) {
      return "";
    }
    if (value instanceof byte[] bytes) {
      return isPrintable(bytes)
          ? "\"" + new String(bytes, StandardCharsets.US_ASCII) + "\""
          : abbreviateHex(bytes);
    }
    if (value instanceof BigInteger integer && integer.bitLength() > 64) {
      return abbreviateHex(integer.toByteArray());
    }
    return element
        .getFacet(RbelAsn1OidFacet.class)
        .flatMap(oid -> oid.getName().printValue())
        .map(name -> value + " (" + name + ")")
        .orElseGet(value::toString);
  }

  private static boolean isPrintable(byte[] bytes) {
    if (bytes.length < 2) {
      return false;
    }
    for (byte b : bytes) {
      if (b < 0x20 || b > 0x7E) {
        return false;
      }
    }
    return true;
  }

  private static String abbreviateHex(byte[] bytes) {
    final String hex = Hex.toHexString(bytes);
    if (hex.length() <= MAX_VALUE_LENGTH) {
      return hex;
    }
    return hex.substring(0, ABBREVIATED_HEAD_LENGTH)
        + "…"
        + hex.substring(hex.length() - ABBREVIATED_TAIL_LENGTH)
        + " ("
        + bytes.length
        + " bytes)";
  }
}
