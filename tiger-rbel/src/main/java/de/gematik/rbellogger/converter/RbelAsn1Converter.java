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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BoundedInputStream;
import org.bouncycastle.asn1.*;
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
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.ASN1UTCTime;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERPrintableString;

@ConverterInfo(onlyActivateFor = "asn1")
@Slf4j
public class RbelAsn1Converter implements RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter context) {
    if (!tryToParseAsn1Structure(
            new ByteArrayInputStream(rbelElement.getRawContent()), context, rbelElement)
        && !safeConvertBase64Using(rbelElement, Base64.getDecoder(), context)) {
      safeConvertBase64Using(rbelElement, Base64.getUrlDecoder(), context);
    }
  }

  private boolean safeConvertBase64Using(
      RbelElement rbelElement, Decoder decoder, RbelConverter context) {
    InputStream data;

    try {
      data = decoder.wrap(withoutTrailingNewLine(rbelElement.getRawContent()));
    } catch (IllegalArgumentException e) {
      return false;
    }
    if (data != null) {
      return tryToParseAsn1Structure(data, context, rbelElement);
    }
    return false;
  }

  @SneakyThrows
  private InputStream withoutTrailingNewLine(byte[] data) {
    if (data.length > 0 && data[data.length - 1] == '\n') {
      if (data.length > 1 && data[data.length - 2] == '\r') {
        return BoundedInputStream.builder().setBufferSize(data.length - 2).setByteArray(data).get();
      }
      return BoundedInputStream.builder().setBufferSize(data.length - 1).setByteArray(data).get();
    }
    return new ByteArrayInputStream(data);
  }

  private boolean tryToParseAsn1Structure(
      InputStream data, RbelConverter converter, RbelElement parentNode) {
    try (ASN1InputStream input = new ASN1InputStream(data)) {
      ASN1Primitive primitive;
      while ((primitive = input.readObject()) != null) {
        if (parentNode.hasFacet(RbelAsn1Facet.class)) {
          if (Arrays.equals(primitive.getEncoded(), parentNode.getRawContent())) {
            return true;
          } else {
            log.debug("Stream with multiple ASN.1 Instances encountered! Skipping");
            return false;
          }
        }

        try {
          convertToAsn1Facets(ASN1Sequence.getInstance(primitive), converter, parentNode);
        } catch (IllegalArgumentException e) {
          return false;
        }
        if (input.available() != 0) {
          log.warn(
              "Found a ASN.1-Stream with more then a single element. The rest of the element will"
                  + " be skipped");
          parentNode.addFacet(
              new RbelAsn1UnparsedBytesFacet(new RbelElement(input.readAllBytes(), parentNode)));
        }
        parentNode.addFacet(new RbelRootFacet<>(parentNode.getFacetOrFail(RbelAsn1Facet.class)));
      }
      return true;
    } catch (IOException e) {
      log.trace("Error while parsing element", e);
      return false;
    }
  }

  private void convertToAsn1Facets(
      ASN1Encodable asn1, RbelConverter converter, RbelElement parentNode) throws IOException {
    parentNode.addFacet(RbelAsn1Facet.builder().asn1Content(asn1).build());
    if ((asn1 instanceof ASN1Sequence) || (asn1 instanceof ASN1Set)) {
      List<RbelElement> rbelSequence = new ArrayList<>();
      for (ASN1Encodable encodable : (Iterable<ASN1Encodable>) asn1) {
        RbelElement newChild =
            new RbelElement(encodable.toASN1Primitive().getEncoded(), parentNode);
        convertToAsn1Facets(encodable, converter, newChild);
        rbelSequence.add(newChild);
      }
      parentNode.addFacet(RbelListFacet.builder().childNodes(rbelSequence).build());
    } else if (asn1 instanceof ASN1TaggedObject asn1TaggedObject) {
      final int tagNo = asn1TaggedObject.getTagNo();
      final ASN1Primitive nestedObject = asn1TaggedObject.getBaseObject().toASN1Primitive();
      RbelElement nestedElement = new RbelElement(nestedObject.getEncoded(), parentNode);
      convertToAsn1Facets(nestedObject, converter, nestedElement);
      parentNode.addFacet(
          new RbelAsn1TaggedValueFacet(
              RbelElement.wrap(BigInteger.valueOf(tagNo).toByteArray(), parentNode, tagNo),
              nestedElement));
    } else if (asn1 instanceof ASN1Integer asn1Integer) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Integer.getValue()).build());
    } else if (asn1 instanceof ASN1ObjectIdentifier asn1ObjectIdentifier) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1ObjectIdentifier.getId()).build());
    } else if (asn1 instanceof ASN1OctetString asn1OctetString) {
      final byte[] octets = asn1OctetString.getOctets();
      parentNode.addFacet(RbelValueFacet.builder().value(octets).build());
      tryToParseEmbededContentAndAddFacetIfPresent(converter, parentNode, octets);
    } else if (asn1 instanceof ASN1BitString asn1BitString) {
      final byte[] octets = asn1BitString.getOctets();
      parentNode.addFacet(RbelValueFacet.builder().value(octets).build());
      tryToParseEmbededContentAndAddFacetIfPresent(converter, parentNode, octets);
    } else if (asn1 instanceof ASN1String asn1String) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1String.getString()).build());
      tryToParseEmbededContentAndAddFacetIfPresent(
          converter, parentNode, ((ASN1Primitive) asn1).getEncoded());
      addCharsetInformation(parentNode, asn1);
    } else if (asn1 instanceof ASN1Boolean asn1Boolean) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Boolean.isTrue()).build());
    } else if (asn1 instanceof ASN1Null) {
      parentNode.addFacet(RbelValueFacet.builder().value(null).build());
    } else if (asn1 instanceof ASN1UTCTime asn1UTCTime) {
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
    } else if (asn1 instanceof ASN1GeneralizedTime asn1GeneralizedTime) {
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
    } else if (asn1 instanceof ASN1Enumerated asn1Enumerated) {
      parentNode.addFacet(RbelValueFacet.builder().value(asn1Enumerated.getValue()).build());
    } else {
      log.warn("Unable to convert " + asn1.getClass().getSimpleName() + "!");
    }
  }

  private void addCharsetInformation(RbelElement parentNode, ASN1Encodable asn1) {
    if (asn1 instanceof DERPrintableString || asn1 instanceof DERIA5String) {
      parentNode.setCharset(Optional.of(StandardCharsets.US_ASCII));
    } else {
      parentNode.setCharset(Optional.of(StandardCharsets.UTF_8));
    }
  }

  private void tryToParseEmbededContentAndAddFacetIfPresent(
      RbelConverter converter, RbelElement parentNode, byte[] octets) {
    RbelElement nestedElement = new RbelElement(octets, parentNode);
    converter.convertElement(nestedElement);
    parentNode.addFacet(new RbelNestedFacet(nestedElement));
  }
}
