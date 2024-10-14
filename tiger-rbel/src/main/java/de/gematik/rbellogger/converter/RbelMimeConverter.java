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
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;

@ConverterInfo(onlyActivateFor = "mime")
@Slf4j
public class RbelMimeConverter implements RbelConverterPlugin {

  private static final Pattern AUTHENTICATED_ENVELOPED_DATA =
      Pattern.compile(
          "application/pkcs7-mime\\s*;.*smime-type=authenticated-enveloped-data.*",
          Pattern.DOTALL);

  private static final String TRANSFER_ENCODING_7_BIT = "7bit";
  public static final String CONTENT_TRANSFER_ENCODING = "content-transfer-encoding";

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    Optional.ofNullable(element.getParentNode())
        .filter(
            parent ->
                parent.hasFacet(RbelPop3ResponseFacet.class)
                    || parent.hasFacet(RbelDecryptedEmailFacet.class)
                    || parent.hasFacet(RbelSmtpCommandFacet.class))
        .map(facet -> element.getRawContent())
        .ifPresent(content -> new Parser(context).parseEntity(element, parseMimeMessage(content)));
  }

  private record Parser(RbelConverter context) {

    private RbelElement parseEntity(RbelElement element, Entity message) {
      RbelMimeMessageFacet messageFacet = buildMessageFacet(element, message);
      element.addFacet(messageFacet);
      element.addFacet(new RbelRootFacet<>(messageFacet));
      messageFacet
          .header()
          .getFacet(RbelMimeHeaderFacet.class)
          .map(header -> header.get("content-type"))
          .map(RbelElement::getRawStringContent)
          .map(AUTHENTICATED_ENVELOPED_DATA::matcher)
          .filter(Matcher::matches)
          .ifPresent(m -> context.convertElement(messageFacet.body()));
      return element;
    }

    @SneakyThrows
    private RbelMimeMessageFacet buildMessageFacet(RbelElement element, Entity message) {
      var body = parseBody(element, message.getBody());
      var header = parseHeader(element, message.getHeader());
      return RbelMimeMessageFacet.builder().header(header).body(body).build();
    }

    private RbelElement parseHeader(RbelElement element, Header messageHeader) {
      var headerFacet = new RbelMimeHeaderFacet();
      RbelElement headerElement = RbelElement.wrap(element, messageHeader.toString());
      messageHeader
          .getFieldsAsMap()
          .forEach(
              (name, values) ->
                  values.stream()
                      .map(Field::getBody)
                      .map(value -> RbelElement.wrap(headerElement, value))
                      .forEach(valueElement -> headerFacet.put(name, valueElement)));

      return headerElement.addFacet(headerFacet).addFacet(new RbelRootFacet<>(headerFacet));
    }

    @SneakyThrows
    private RbelElement parseBody(RbelElement element, Body body) {
      if (body instanceof Multipart multipart) {
        return parseMultiPartBody(element, multipart);
      } else if (body instanceof SingleBody singleBody) {
        return parseSingleBody(element, singleBody);
      } else if (body instanceof Message message) {
        return parseEntity(createChildNode(element), message);
      } else {
        throw new RbelConversionException( // NOSONAR
            "unknown message body type: " + body.getClass().getName());
      }
    }

    @SneakyThrows
    private RbelElement parseSingleBody(RbelElement element, SingleBody singleBody) {
      var bytesAndContent = extractContent(singleBody);
      var bytes = bytesAndContent.getLeft();
      var content = bytesAndContent.getRight();

      return context.convertElement(createBodyElementAndFacet(element, bytes, content));
    }

    private RbelElement createBodyElementAndFacet(
        RbelElement element, byte[] bytes, String content) {
      var bodyFacet = new RbelMimeBodyFacet(content);

      return new RbelElement(bytes, element)
          .addFacet(bodyFacet)
          .addFacet(new RbelRootFacet<>(bodyFacet));
    }

    private Pair<byte[], String> extractContent(SingleBody singleBody) throws IOException {
      if (singleBody instanceof TextBody textBody) {
        try (var reader = textBody.getReader();
            var writer = new StringWriter()) {
          reader.transferTo(writer);
          var content = writer.toString();
          return Pair.of(content.getBytes(), content);
        }
      } else {
        try (var in = singleBody.getInputStream();
            var out =
                new ByteArrayOutputStream() {
                  public byte[] getBytes() {
                    return buf;
                  }
                }) {
          in.transferTo(out);
          var bytes = out.getBytes();
          String content =
              Optional.ofNullable(
                      singleBody.getParent().getHeader().getField(CONTENT_TRANSFER_ENCODING))
                  .map(Field::getBody)
                  .filter(TRANSFER_ENCODING_7_BIT::equals)
                  .map(encoding -> new String(bytes))
                  .orElseGet(() -> Base64.getEncoder().encodeToString(bytes));
          return Pair.of(bytes, content);
        }
      }
    }

    private RbelElement parseMultiPartBody(RbelElement parentElement, Multipart multipart) {
      var element = createChildNode(parentElement);
      var parts = parseMultipart(element, multipart);
      var preamble = buildElementIfPresent(element, multipart.getPreamble());
      var epilogue = buildElementIfPresent(element, multipart.getEpilogue());
      var bodyFacet =
          RbelMimeMultipartFacet.builder()
              .preamble(preamble)
              .parts(parts)
              .epilogue(epilogue)
              .build();
      return element.addFacet(bodyFacet);
    }

    private RbelElement parseMultipart(RbelElement parentElement, Multipart multipart) {
      var element = createChildNode(parentElement);
      var parts = new ArrayList<RbelElement>(multipart.getCount());
      multipart.getBodyParts().stream()
          .map(part -> parseEntity(new RbelElement(null, element), part))
          .forEach(parts::add);
      return element.addFacet(new RbelListFacet(parts));
    }
  }

  @SneakyThrows
  public static Message parseMimeMessage(byte[] bytes) {
    return new DefaultMessageBuilder().parseMessage(new ByteArrayInputStream(bytes));
  }

  private static RbelElement createChildNode(RbelElement element) {
    return new RbelElement(element.getRawContent(), element);
  }

  private static RbelElement buildElementIfPresent(RbelElement element, String value) {
    return Optional.ofNullable(value).map(s -> RbelElement.wrap(element, s)).orElse(null);
  }
}
