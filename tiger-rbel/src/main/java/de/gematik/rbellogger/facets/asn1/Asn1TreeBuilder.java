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

import static de.gematik.rbellogger.facets.pki.OidDictionary.buildAndAddAsn1OidFacet;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelNestedFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.RbelValueFacet;
import de.gematik.rbellogger.util.RbelContent;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Null;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1PrintableString;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;

/**
 * Builds the Rbel tree of BER encoded content. The structure is determined completely before the
 * tree is built, so a failed attempt never leaves anything behind. Every node is a slice of the
 * parsed bytes.
 */
@Slf4j
@RequiredArgsConstructor
class Asn1TreeBuilder {

  private static final int SEQUENCE_TAG = 0x30;
  private static final char FIRST_BASE64_CHAR_OF_SEQUENCE = 'M';

  private final RbelConversionExecutor converter;

  enum Mode {
    /**
     * Exactly one SEQUENCE, which the element itself represents. Protects against interpreting
     * arbitrary bytes as ASN.1.
     */
    SEQUENCE,
    /** Exactly one data object of any type, which the element itself represents. */
    SINGLE_OBJECT,
    /**
     * One or more data objects of any type. The element contains them, keyed like the children of a
     * constructed data object, so a single object is addressed the same way as several.
     */
    OBJECTS;

    boolean accepts(List<BerTlv> tlvs) {
      return switch (this) {
        case SEQUENCE -> tlvs.size() == 1 && tlvs.get(0).tag() == SEQUENCE_TAG;
        case SINGLE_OBJECT -> tlvs.size() == 1;
        case OBJECTS -> true;
      };
    }
  }

  /** Parses the raw content or, failing that, its Base64 or Base64url decoding. */
  Optional<RbelAsn1Facet> parse(RbelElement element, Mode mode) {
    if (element.getContent().size() < 3
        || element.hasFacet(RbelAsn1Facet.class)
        || element.hasFacet(RbelRootFacet.class)) {
      return Optional.empty();
    }
    final RbelContent content = element.getContent();
    if (mode != Mode.SEQUENCE || content.get(0) == SEQUENCE_TAG) {
      final Optional<RbelAsn1Facet> result = parse(element, content, mode);
      if (result.isPresent()) {
        return result;
      }
    }
    return decodeBase64(element, mode, Base64.getDecoder())
        .or(() -> decodeBase64(element, mode, Base64.getUrlDecoder()))
        .flatMap(decoded -> parse(element, decoded, mode));
  }

  private Optional<RbelAsn1Facet> parse(RbelElement element, RbelContent source, Mode mode) {
    return BerTlv.parseAll(source.toByteArray())
        .filter(mode::accepts)
        .map(
            tlvs ->
                mode == Mode.OBJECTS
                    ? buildSeries(element, source, tlvs)
                    : buildNode(element, source, tlvs.get(0)));
  }

  private static Optional<RbelContent> decodeBase64(
      RbelElement element, Mode mode, Base64.Decoder decoder) {
    if (mode == Mode.SEQUENCE && !startsWithBase64OfSequence(element.getContent())) {
      return Optional.empty();
    }
    final String trimmed = element.getRawStringContent().strip();
    try {
      return Optional.of(RbelContent.of(decoder.decode(trimmed)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static boolean startsWithBase64OfSequence(RbelContent content) {
    for (int i = 0; i < content.size(); i++) {
      if (!Character.isWhitespace(content.get(i))) {
        return content.get(i) == FIRST_BASE64_CHAR_OF_SEQUENCE;
      }
    }
    return false;
  }

  private RbelAsn1Facet buildSeries(RbelElement element, RbelContent source, List<BerTlv> tlvs) {
    final List<RbelElement> children = createChildren(element, source, tlvs);
    final RbelAsn1Facet facet = new RbelAsn1Facet(null, keyChildren(children, tlvs), source);
    element.addFacet(facet);
    buildChildren(children, source, tlvs);
    return facet;
  }

  private RbelAsn1Facet buildNode(RbelElement node, RbelContent source, BerTlv tlv) {
    final RbelContent encoding = source.subArray(tlv.offset(), tlv.end());
    if (tlv.isConstructed()) {
      final List<RbelElement> children = createChildren(node, source, tlv.children());
      final RbelAsn1Facet facet =
          new RbelAsn1Facet(tlv.tagHex(), keyChildren(children, tlv.children()), encoding);
      node.addFacet(facet);
      buildChildren(children, source, tlv.children());
      return facet;
    }
    final RbelAsn1Facet facet = new RbelAsn1Facet(tlv.tagHex(), new RbelMultiMap<>(), encoding);
    node.addFacet(facet);
    if (tlv.isUniversal()) {
      convertUniversalPrimitive(node, facet);
    } else {
      final byte[] value = source.toByteArray(tlv.valueOffset(), tlv.end());
      node.addFacet(RbelValueFacet.of(value));
      tryToParseEmbeddedContentAndAddFacetIfPresent(node, value);
    }
    return facet;
  }

  private static List<RbelElement> createChildren(
      RbelElement parent, RbelContent source, List<BerTlv> tlvs) {
    final List<RbelElement> children = new ArrayList<>(tlvs.size());
    for (BerTlv tlv : tlvs) {
      children.add(RbelElement.create(source.subArray(tlv.offset(), tlv.end()), parent));
    }
    return children;
  }

  private static RbelMultiMap<RbelElement> keyChildren(
      List<RbelElement> children, List<BerTlv> tlvs) {
    final RbelMultiMap<RbelElement> result = new RbelMultiMap<>();
    for (int i = 0; i < children.size(); i++) {
      final BerTlv tlv = tlvs.get(i);
      result.put(tlv.isUniversal() ? String.valueOf(i) : tlv.tagHex(), children.get(i));
    }
    return result;
  }

  private void buildChildren(List<RbelElement> children, RbelContent source, List<BerTlv> tlvs) {
    for (int i = 0; i < children.size(); i++) {
      buildNode(children.get(i), source, tlvs.get(i));
    }
  }

  private void convertUniversalPrimitive(RbelElement node, RbelAsn1Facet facet) {
    final ASN1Encodable asn1 = facet.getAsn1Content();
    if (asn1 == null) {
      return;
    }
    try {
      if (asn1 instanceof ASN1Integer asn1Integer) {
        node.addFacet(RbelValueFacet.of(asn1Integer.getValue()));
      } else if (asn1 instanceof ASN1ObjectIdentifier oid) {
        node.addFacet(RbelValueFacet.of(oid.getId()));
        buildAndAddAsn1OidFacet(node, oid.getId());
      } else if (asn1 instanceof ASN1OctetString octetString) {
        addBinaryValue(node, octetString.getOctets());
      } else if (asn1 instanceof ASN1BitString bitString) {
        addBinaryValue(node, bitString.getOctets());
      } else if (asn1 instanceof ASN1PrintableString printableString) {
        addStringValue(node, asn1, printableString.getString(), printableString.getOctets());
      } else if (asn1 instanceof ASN1String string) {
        addStringValue(
            node, asn1, string.getString(), string.getString().getBytes(charsetOf(asn1)));
      } else if (asn1 instanceof ASN1Boolean asn1Boolean) {
        node.addFacet(RbelValueFacet.of(asn1Boolean.isTrue()));
      } else if (asn1 instanceof ASN1Null) {
        node.addFacet(RbelValueFacet.of(null));
      } else if (asn1 instanceof ASN1UTCTime utcTime) {
        node.addFacet(RbelValueFacet.of(toZonedDateTime(utcTime.getAdjustedDate())));
      } else if (asn1 instanceof ASN1GeneralizedTime generalizedTime) {
        node.addFacet(RbelValueFacet.of(toZonedDateTime(generalizedTime.getDate())));
      } else if (asn1 instanceof ASN1Enumerated enumerated) {
        node.addFacet(RbelValueFacet.of(enumerated.getValue()));
      } else {
        log.warn("Unable to convert {}!", asn1.getClass().getSimpleName());
      }
    } catch (ParseException | RuntimeException e) {
      log.trace("Could not decode value of {}, keeping it binary", asn1, e);
    }
  }

  private static ZonedDateTime toZonedDateTime(Date date) {
    return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC"));
  }

  private void addBinaryValue(RbelElement node, byte[] octets) {
    node.addFacet(RbelValueFacet.of(octets));
    tryToParseEmbeddedContentAndAddFacetIfPresent(node, octets);
  }

  private void addStringValue(RbelElement node, ASN1Encodable asn1, String value, byte[] octets) {
    node.addFacet(RbelValueFacet.of(value));
    node.setCharset(Optional.of(charsetOf(asn1)));
    tryToParseEmbeddedContentAndAddFacetIfPresent(node, octets);
  }

  private static Charset charsetOf(ASN1Encodable asn1) {
    return asn1 instanceof DERPrintableString || asn1 instanceof DERIA5String
        ? StandardCharsets.US_ASCII
        : StandardCharsets.UTF_8;
  }

  private void tryToParseEmbeddedContentAndAddFacetIfPresent(RbelElement node, byte[] octets) {
    final RbelElement nestedElement = new RbelElement(octets, node);
    val facet = new RbelNestedFacet(nestedElement);
    try {
      node.addFacet(facet);
      converter.convertElement(nestedElement);
      nestedElement.getFacets().stream()
          .filter(RbelRootFacet.class::isInstance)
          .map(RbelRootFacet.class::cast)
          .filter(f -> f.getRootFacet() instanceof RbelAsn1Facet)
          .toList()
          .forEach(nestedElement::removeFacet);
    } catch (RuntimeException e) {
      node.removeFacet(facet);
    }
  }
}
