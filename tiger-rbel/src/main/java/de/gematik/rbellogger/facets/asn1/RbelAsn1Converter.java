/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.facets.asn1;

import static de.gematik.rbellogger.facets.pki.OidDictionary.buildAndAddAsn1OidFacet;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.util.RbelException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Base64.Decoder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.input.BoundedInputStream;
import org.bouncycastle.asn1.ASN1BitString;
import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Null;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1PrintableString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;

@ConverterInfo(onlyActivateFor = "asn1")
@Slf4j
public class RbelAsn1Converter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor context) {
    // Avoid re-parsing of ASN.1 structures already parsed by other converters.
    // This might happen if the element is an X.509 certificate or an OCSP-Response
    if (rbelElement.getContent().size() < 3 || rbelElement.hasFacet(RbelRootFacet.class)) {
      return;
    }

    tryToParseAsn1Structure(rbelElement.getContent().toInputStream(), context, rbelElement)
        .or(
            () ->
                decodeIntoInputStream(rbelElement, Base64.getDecoder())
                    .flatMap(stream -> tryToParseAsn1Structure(stream, context, rbelElement)))
        .or(
            () ->
                decodeIntoInputStream(rbelElement, Base64.getUrlDecoder())
                    .flatMap(stream -> tryToParseAsn1Structure(stream, context, rbelElement)))
        .ifPresent(facet -> rbelElement.addFacet(new RbelRootFacet<>(facet)));
  }

  private Optional<InputStream> decodeIntoInputStream(RbelElement rbelElement, Decoder decoder) {
    try {
      return Optional.ofNullable(decoder.wrap(trimmedInputStream(rbelElement)));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  @SneakyThrows
  private InputStream trimmedInputStream(RbelElement element) {
    byte[] data = element.getContent().toByteArray();
    int start = 0;
    int end = data.length;
    while (start < end && Character.isWhitespace((char) data[start])) {
      start++;
    }
    while (end > start && Character.isWhitespace((char) data[end - 1])) {
      end--;
    }
    final BoundedInputStream result =
        BoundedInputStream.builder()
            .setInputStream(new ByteArrayInputStream(data))
            .setMaxCount(end)
            .get();
    if (start != result.skip(start)) {
      throw new RbelConversionException("Error while skipping whitespace", element, this);
    }
    return result;
  }

  @SneakyThrows
  private Optional<RbelAsn1Facet> tryToParseAsn1Structure(
      InputStream data, RbelConversionExecutor converter, RbelElement element) {
    if (data.available() < 3 || element.hasFacet(RbelAsn1Facet.class)) {
      return Optional.empty();
    }
    Optional<RbelAsn1Facet> result = Optional.empty();
    try (ASN1InputStream input = new ASN1InputStream(data)) {
      ASN1Primitive primitive;
      while ((primitive = input.readObject()) != null) {
        if (element.hasFacet(RbelAsn1Facet.class)
            || element.hasFacet(RbelAsn1TaggedValueFacet.class)) {
          log.trace("Stream with multiple ASN.1 Instances encountered! Skipping");
          return Optional.empty();
        }

        if (primitive instanceof ASN1Sequence
            || element.getParentNode() != null
                && (element.getParentNode().hasFacet(RbelAllowAsn1FragmentsFacet.class)
                    || element.getParentNode().hasFacet(RbelAsn1Facet.class))) {
          result = Optional.ofNullable(convertToAsn1Facet(primitive, converter, element));
        }

        if (input.available() != 0) {
          result.ifPresent(facet -> element.getFacets().remove(facet));
          return Optional.empty();
        }
      }
      return result;
    } catch (IOException | RuntimeException e) {
      log.trace("Error while parsing element {}", element, e);
      return Optional.empty();
    }
  }

  private RbelAsn1Facet convertToAsn1Facet(
      ASN1Encodable asn1, RbelConversionExecutor converter, RbelElement parentNode)
      throws IOException {
    val result = RbelAsn1Facet.builder().asn1Content(asn1).build();
    parentNode.addFacet(result);
    if ((asn1 instanceof ASN1Sequence) || (asn1 instanceof ASN1Set)) {
      convertSequence((Iterable<ASN1Encodable>) asn1, converter, parentNode);
    } else if (asn1 instanceof ASN1TaggedObject asn1TaggedObject) {
      convertTaggedObject(converter, parentNode, asn1TaggedObject);
    } else if (asn1 instanceof ASN1Integer asn1Integer) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Integer.getValue()).build());
    } else if (asn1 instanceof ASN1ObjectIdentifier asn1ObjectIdentifier) {
      convertOid(parentNode, asn1ObjectIdentifier);
    } else if (asn1 instanceof ASN1OctetString asn1OctetString) {
      convertOctetString(converter, parentNode, asn1OctetString);
    } else if (asn1 instanceof ASN1BitString asn1BitString) {
      convertBitString(converter, parentNode, asn1BitString);
    } else if (asn1 instanceof ASN1PrintableString asn1String) {
      convertPrintableString(asn1, converter, parentNode, asn1String);
    } else if (asn1 instanceof ASN1String asn1String) {
      convertString(asn1, converter, parentNode, asn1String);
    } else if (asn1 instanceof ASN1Boolean asn1Boolean) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Boolean.isTrue()).build());
    } else if (asn1 instanceof ASN1Null) {
      parentNode.addFacet(RbelValueFacet.builder().value(null).build());
    } else if (asn1 instanceof ASN1UTCTime asn1UTCTime) {
      convertUtcTime(asn1, parentNode, asn1UTCTime);
    } else if (asn1 instanceof ASN1GeneralizedTime asn1GeneralizedTime) {
      convertGeneralizedTime(asn1, parentNode, asn1GeneralizedTime);
    } else if (asn1 instanceof ASN1Enumerated asn1Enumerated) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Enumerated.getValue()).build());
    } else {
      log.warn("Unable to convert " + asn1.getClass().getSimpleName() + "!");
    }
    return result;
  }

  private static void convertGeneralizedTime(
      ASN1Encodable asn1, RbelElement parentNode, ASN1GeneralizedTime asn1GeneralizedTime) {
    try {
      parentNode.addFacet(
          RbelValueFacet.builder()
              .value(
                  ZonedDateTime.ofInstant(
                      asn1GeneralizedTime.getDate().toInstant(), ZoneId.of("UTC")))
              .build());
    } catch (ParseException e) {
      throw new RbelException("Error during time-conversion of " + asn1, e);
    }
  }

  private static void convertUtcTime(
      ASN1Encodable asn1, RbelElement parentNode, ASN1UTCTime asn1UTCTime) {
    try {
      parentNode.addFacet(
          RbelValueFacet.builder()
              .value(
                  ZonedDateTime.ofInstant(
                      asn1UTCTime.getAdjustedDate().toInstant(), ZoneId.of("UTC")))
              .build());
    } catch (ParseException e) {
      throw new RbelException("Error during time-conversion of " + asn1, e);
    }
  }

  @SneakyThrows
  private void convertString(
      ASN1Encodable asn1,
      RbelConversionExecutor converter,
      RbelElement parentNode,
      ASN1String asn1String) {
    parentNode.addFacet(RbelValueFacet.builder().value(asn1String.getString()).build());
    addCharsetInformation(parentNode, asn1);
    tryToParseEmbeddedContentAndAddFacetIfPresent(
        converter, parentNode, asn1String.getString().getBytes(parentNode.getElementCharset()));
  }

  private void convertPrintableString(
      ASN1Encodable asn1,
      RbelConversionExecutor converter,
      RbelElement parentNode,
      ASN1PrintableString asn1String) {
    parentNode.addFacet(RbelValueFacet.builder().value(asn1String.getString()).build());
    addCharsetInformation(parentNode, asn1);
    tryToParseEmbeddedContentAndAddFacetIfPresent(converter, parentNode, asn1String.getOctets());
  }

  private void convertBitString(
      RbelConversionExecutor converter, RbelElement parentNode, ASN1BitString asn1BitString) {
    final byte[] octets = asn1BitString.getOctets();
    parentNode.addFacet(RbelValueFacet.builder().value(octets).build());
    tryToParseEmbeddedContentAndAddFacetIfPresent(converter, parentNode, octets);
  }

  private void convertOctetString(
      RbelConversionExecutor converter, RbelElement parentNode, ASN1OctetString asn1OctetString) {
    final byte[] octets = asn1OctetString.getOctets();
    parentNode.addFacet(RbelValueFacet.builder().value(octets).build());
    tryToParseEmbeddedContentAndAddFacetIfPresent(converter, parentNode, octets);
  }

  private static void convertOid(
      RbelElement parentNode, ASN1ObjectIdentifier asn1ObjectIdentifier) {
    parentNode.addFacet(RbelValueFacet.builder().value(asn1ObjectIdentifier.getId()).build());
    buildAndAddAsn1OidFacet(parentNode, asn1ObjectIdentifier.getId());
  }

  private void convertTaggedObject(
      RbelConversionExecutor converter, RbelElement parentNode, ASN1TaggedObject asn1TaggedObject)
      throws IOException {
    final int tagNo = asn1TaggedObject.getTagNo();
    final ASN1Primitive nestedObject = asn1TaggedObject.getBaseObject().toASN1Primitive();
    RbelElement nestedElement = new RbelElement(nestedObject.getEncoded(), parentNode);
    convertToAsn1Facet(nestedObject, converter, nestedElement);
    parentNode.addFacet(
        new RbelAsn1TaggedValueFacet(
            RbelElement.wrap(BigInteger.valueOf(tagNo).toByteArray(), parentNode, tagNo),
            nestedElement));
  }

  private void convertSequence(
      Iterable<ASN1Encodable> asn1, RbelConversionExecutor converter, RbelElement parentNode)
      throws IOException {
    List<RbelElement> rbelSequence = new ArrayList<>();
    for (ASN1Encodable encodable : asn1) {
      RbelElement newChild = new RbelElement(encodable.toASN1Primitive().getEncoded(), parentNode);
      convertToAsn1Facet(encodable, converter, newChild);
      rbelSequence.add(newChild);
    }
    parentNode.addFacet(RbelListFacet.builder().childNodes(rbelSequence).build());
  }

  private void addCharsetInformation(RbelElement parentNode, ASN1Encodable asn1) {
    if (asn1 instanceof DERPrintableString || asn1 instanceof DERIA5String) {
      parentNode.setCharset(Optional.of(StandardCharsets.US_ASCII));
    } else {
      parentNode.setCharset(Optional.of(StandardCharsets.UTF_8));
    }
  }

  private void tryToParseEmbeddedContentAndAddFacetIfPresent(
      RbelConversionExecutor converter, RbelElement parentNode, byte[] octets) {
    RbelElement nestedElement = new RbelElement(octets, parentNode);
    val facet = new RbelNestedFacet(nestedElement);
    try {
      parentNode.addFacet(facet);
      converter.convertElement(nestedElement);
      nestedElement.getFacets().stream()
          .filter(RbelRootFacet.class::isInstance)
          .map(RbelRootFacet.class::cast)
          .filter(f -> f.getRootFacet() instanceof RbelAsn1Facet)
          .toList()
          .forEach(f -> nestedElement.getFacets().remove(f));
    } catch (RuntimeException e) {
      parentNode.getFacets().remove(facet);
    }
  }

  /**
   * Marker-facet. This indicates that an ASN.1 fragment (a non-nested structure, e.g. a naked date)
   * may stand for itself (without a parent ASN.1 node).
   */
  @Data
  @AllArgsConstructor
  @RequiredArgsConstructor
  public static class RbelAllowAsn1FragmentsFacet implements RbelFacet {
    private boolean shouldParse = true;
  }
}
