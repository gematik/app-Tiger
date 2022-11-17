/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelAsn1Exception;
import de.gematik.rbellogger.util.RbelException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Base64.Decoder;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.*;

@Slf4j
public class RbelAsn1Converter implements RbelConverterPlugin {

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter context) {
        if (!tryToParseAsn1Structure(rbelElement.getRawContent(), context, rbelElement)) {
            if (!safeConvertBase64Using(rbelElement, Base64.getDecoder(), context)) {
                safeConvertBase64Using(rbelElement, Base64.getUrlDecoder(), context);
            }
        }
    }

    private boolean safeConvertBase64Using(RbelElement rbelElement, Decoder decoder, RbelConverter context) {
        byte[] data;
        try {
            data = decoder.decode(rbelElement.getRawContent());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (data != null) {
            return tryToParseAsn1Structure(data, context, rbelElement);
        }
        return false;
    }

    private boolean tryToParseAsn1Structure(byte[] data, RbelConverter converter, RbelElement parentNode) {
        try (ASN1InputStream input = new ASN1InputStream(data)) {
            ASN1Primitive primitive;
            while ((primitive = input.readObject()) != null) {
                if (parentNode.hasFacet(RbelAsn1Facet.class)) {
                    // RBEL-38 TODO hacky workaround
                    if (Arrays.equals(primitive.getEncoded(), parentNode.getRawContent())) {
                        return true;
                    } else {
                        log.debug("Stream with multiple ASN.1 Instances encountered! Skipping");
                        return false;
                    }
                }

                ASN1Sequence asn1;
                try {
                    asn1 = ASN1Sequence.getInstance(primitive);
                } catch (IllegalArgumentException e) {
                    return false;
                }
                convertToAsn1Facets(asn1, converter, parentNode);
                if (input.available() != 0) {
                    log.warn(
                        "Found a ASN.1-Stream with more then a single element. The rest of the element will be skipped");
                    parentNode.addFacet(new RbelAsn1UnparsedBytesFacet(
                        new RbelElement(input.readAllBytes(), parentNode)));
                }
                parentNode.addFacet(new RbelRootFacet<>(parentNode.getFacetOrFail(RbelAsn1Facet.class)));
            }
            return true;
        } catch (IOException e) {
            log.trace("Error while parsing element", e);
            return false;
        }
    }

    private void convertToAsn1Facets(ASN1Encodable asn1, RbelConverter converter, RbelElement parentNode)
        throws IOException {
        parentNode.addFacet(RbelAsn1Facet.builder().asn1Content(asn1).build());
        if ((asn1 instanceof ASN1Sequence)
            || (asn1 instanceof ASN1Set)) {
            List<RbelElement> rbelSequence = new ArrayList<>();
            for (ASN1Encodable encodable : (Iterable<ASN1Encodable>) asn1) {
                RbelElement newChild = new RbelElement(encodable.toASN1Primitive().getEncoded(), parentNode);
                convertToAsn1Facets(encodable, converter, newChild);
                rbelSequence.add(newChild);
            }
            parentNode.addFacet(RbelListFacet.builder().childNodes(rbelSequence).build());
        } else if (asn1 instanceof ASN1TaggedObject) {
            final int tagNo = ((ASN1TaggedObject) asn1).getTagNo();
            final ASN1Primitive nestedObject = ((ASN1TaggedObject) asn1).getObject();
            RbelElement nestedElement = new RbelElement(nestedObject.getEncoded(), parentNode);
            convertToAsn1Facets(nestedObject, converter, nestedElement);
            parentNode.addFacet(new RbelAsn1TaggedValueFacet(
                RbelElement.wrap(BigInteger.valueOf(tagNo).toByteArray(), parentNode, tagNo),
                nestedElement));
        } else if (asn1 instanceof ASN1Integer) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(((ASN1Integer) asn1).getValue())
                .build());
        } else if (asn1 instanceof ASN1ObjectIdentifier) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(((ASN1ObjectIdentifier) asn1).getId())
                .build());
        } else if (asn1 instanceof ASN1OctetString) {
            final byte[] octets = ((ASN1OctetString) asn1).getOctets();
            parentNode.addFacet(RbelValueFacet.builder()
                .value(octets)
                .build());
            tryToParseEmbededContentAndAddFacetIfPresent(converter, parentNode, octets);
        } else if (asn1 instanceof ASN1BitString) {
            final byte[] octets = ((ASN1BitString) asn1).getOctets();
            parentNode.addFacet(RbelValueFacet.builder()
                .value(octets)
                .build());
            tryToParseEmbededContentAndAddFacetIfPresent(converter, parentNode, octets);
        } else if (asn1 instanceof ASN1String) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(((ASN1String) asn1).getString())
                .build());
            tryToParseEmbededContentAndAddFacetIfPresent(converter, parentNode,
                ((ASN1Primitive) asn1).getEncoded());
            addCharsetInformation(parentNode, asn1);
        } else if (asn1 instanceof ASN1Boolean) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(((ASN1Boolean) asn1).isTrue())
                .build());
        } else if (asn1 instanceof ASN1Null) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(null)
                .build());
        } else if (asn1 instanceof ASN1UTCTime) {
            try {
                parentNode.addFacet(RbelValueFacet.builder()
                    .value(ZonedDateTime.ofInstant(
                        ((ASN1UTCTime) asn1).getAdjustedDate().toInstant(), ZoneId.of("UTC")))
                    .build());
            } catch (ParseException e) {
                throw new RbelException("Error during time-conversion of " + asn1, e);
            }
        } else if (asn1 instanceof ASN1GeneralizedTime) {
            try {
                parentNode.addFacet(RbelValueFacet.builder()
                    .value(ZonedDateTime.ofInstant(
                        ((ASN1GeneralizedTime) asn1).getDate().toInstant(), ZoneId.of("UTC")))
                    .build());
            } catch (ParseException e) {
                throw new RbelException("Error during time-conversion of " + asn1, e);
            }
        } else if (asn1 instanceof ASN1Enumerated) {
            parentNode.addFacet(RbelValueFacet.builder()
                .value(((ASN1Enumerated) asn1).getValue())
                .build());
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

    private void tryToParseEmbededContentAndAddFacetIfPresent(RbelConverter converter, RbelElement parentNode,
        byte[] octets) {
        RbelElement nestedElement = new RbelElement(octets, parentNode);
        converter.convertElement(nestedElement);
        parentNode.addFacet(new RbelNestedFacet(nestedElement));
    }
}
