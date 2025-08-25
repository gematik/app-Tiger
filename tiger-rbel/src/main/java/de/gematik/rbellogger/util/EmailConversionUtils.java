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
package de.gematik.rbellogger.util;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.mime.RbelMimeRecipientEmailFacet;
import de.gematik.rbellogger.facets.mime.RbelMimeRecipientEmailsFacet;
import de.gematik.rbellogger.facets.pki.CmsEntityIdentifierFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.Attributes;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailConversionUtils {
  public static final String CRLF = "\r\n";
  public static final byte[] CRLF_BYTES = CRLF.getBytes();
  public static final String CRLF_DOT_CRLF = CRLF + "." + CRLF;
  private static final byte[] DOT_BYTE = ".".getBytes();
  private static final String KOM_LE_SMIME_ATTRIBUTE_RECIPIENT_EMAILS = "1.2.276.0.76.4.173";

  public static RbelElement createChildElement(RbelElement parent, String value) {
    return new RbelElement(value.getBytes(StandardCharsets.UTF_8), parent);
  }

  public static RbelElement parseMailBody(
      RbelElement element, List<RbelContent> lines, int startLine) {
    if (lines.size() > startLine + 1) {
      var body = extractBodyAndRemoveStuffedDots(lines, startLine);
      return RbelElement.builder().content(body).parentNode(element).build();
    }
    return null;
  }

  public static RbelContent removeStuffedDot(RbelContent line) {
    return line.startsWith(DOT_BYTE) ? line.subArray(1, line.size()) : line;
  }

  private static RbelContent extractBodyAndRemoveStuffedDots(
      List<RbelContent> lines, int startLine) {
    RbelContent baseContent = lines.get(0).getBaseContent();
    assert lines.stream().allMatch(line -> line.getBaseContent() == baseContent);
    var bodyLines =
        getMiddleLines(lines, startLine).map(EmailConversionUtils::removeStuffedDot).toList();
    int bodyLength = computeTotalLength(bodyLines.stream());
    int originalLength = computeTotalLength(getMiddleLines(lines, startLine));
    if (bodyLength == originalLength) {
      // no stuffed dots found ==> we can reference the original content
      int firstLineLength = lines.get(0).size();
      return baseContent.subArray(
          firstLineLength, firstLineLength + originalLength - CRLF_BYTES.length);
    }
    return mergeLines(bodyLines);
  }

  private static Integer computeTotalLength(Stream<RbelContent> bodyLinesStream) {
    return bodyLinesStream.map(RbelContent::size).reduce(0, Integer::sum);
  }

  private static Stream<RbelContent> getMiddleLines(List<RbelContent> lines, int startLine) {
    return lines.stream().skip(startLine).limit(lines.size() - 1L - startLine);
  }

  public static RbelContent mergeLines(List<RbelContent> lines) {
    RbelContent content =
        RbelContent.builder()
            .content(lines.stream().map(RbelContent::toByteArray).toList())
            .build();
    // last CRLF needs to be cut off because it belongs to the CRLF_DOT_CRLF sequence
    return content.subArray(0, content.size() - CRLF_BYTES.length);
  }

  public static String duplicateDotsAtLineBegins(String input) {
    return Stream.of(input.split("\r\n", -1))
        .map(line -> line.startsWith(".") ? "." + line : line)
        .collect(Collectors.joining("\r\n"));
  }

  public static RbelElement buildAttributesAndExtractRecipientIds(
      AttributeTable attributes, RbelElement parent, RbelConversionExecutor context)
      throws IOException {
    var attributesElement =
        attributes != null
            ? context.convertElement(attributes.toASN1Structure().getEncoded(), parent)
            : null;
    if (attributes != null) {
      var recipientEmails =
          RbelListFacet.wrap(
              attributesElement, list -> extractRecipientEmails(list, attributes), null);

      attributesElement.addFacet(
          RbelMimeRecipientEmailsFacet.builder().recipientEmails(recipientEmails).build());
    }
    return attributesElement;
  }

  @SneakyThrows
  private static List<RbelElement> extractRecipientEmails(
      RbelElement list, AttributeTable attributeTable) {
    return Optional.ofNullable(attributeTable)
        .map(AttributeTable::toASN1Structure)
        .map(Attributes::getAttributes)
        .stream()
        .flatMap(Stream::of)
        .filter(
            entry -> entry.getAttrType().getId().equals(KOM_LE_SMIME_ATTRIBUTE_RECIPIENT_EMAILS))
        .map(Attribute::getAttributeValues)
        .flatMap(Stream::of)
        .filter(ASN1Sequence.class::isInstance)
        .map(ASN1Sequence.class::cast)
        .map(sequence -> buildRecipientEmail(list, sequence))
        .toList();
  }

  @SneakyThrows
  private static RbelElement buildRecipientEmail(RbelElement list, ASN1Sequence sequence) {
    if (sequence.size() > 1
        && sequence.getObjectAt(0) instanceof ASN1String email
        && sequence.getObjectAt(1) instanceof ASN1Sequence recipientIdentifier) {

      var issuerAndSerialNumber = IssuerAndSerialNumber.getInstance(recipientIdentifier);
      var recipientEmail = new RbelElement(sequence.getEncoded(), list);
      var emailAddress = RbelElement.wrap(recipientEmail, email);
      var recipientId = new RbelElement(null, recipientEmail);
      recipientId.addFacet(
          CmsEntityIdentifierFacet.builder()
              .issuer(RbelElement.wrap(recipientId, issuerAndSerialNumber.getName().toString()))
              .serialNumber(RbelElement.wrap(recipientId, issuerAndSerialNumber.getSerialNumber()))
              .build());
      return recipientEmail.addFacet(
          RbelMimeRecipientEmailFacet.builder()
              .emailAddress(emailAddress)
              .recipientId(recipientId)
              .build());
    } else {
      throw new RbelConversionException("Invalid recipient email sequence: " + sequence.toString());
    }
  }

  public static List<ContainerTag> renderRecipientEmails(
      RbelHtmlRenderingToolkit renderingToolkit, RbelElement unauthAttributes) {
    return Optional.ofNullable(unauthAttributes)
        .flatMap(attributes -> attributes.getFacet(RbelMimeRecipientEmailsFacet.class))
        .map(RbelMimeRecipientEmailsFacet::getRecipientEmails)
        .flatMap(recipients -> recipients.getFacet(RbelListFacet.class))
        .map(RbelListFacet::getChildElements)
        .map(RbelMultiMap::stream)
        .map(
            stream ->
                stream
                    .map(
                        pair ->
                            renderingToolkit.convert(
                                pair.getValue()
                                    .getFacetOrFail(RbelMimeRecipientEmailFacet.class)
                                    .getEmailAddress(),
                                Optional.of(pair.getKey())))
                    .toList())
        .orElse(null);
  }
}
