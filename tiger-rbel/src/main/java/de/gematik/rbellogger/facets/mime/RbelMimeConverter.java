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
package de.gematik.rbellogger.facets.mime;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.facets.smtp.RbelSmtpCommandFacet;
import de.gematik.rbellogger.util.ByteArrayUtils;
import de.gematik.rbellogger.util.RbelContent;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Supplier;
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
import org.apache.james.mime4j.stream.MimeConfig;

@ConverterInfo(onlyActivateFor = {"mime", "email"})
@Slf4j
public class RbelMimeConverter extends RbelConverterPlugin {

  private static final Pattern AUTHENTICATED_ENVELOPED_OR_SIGNED_DATA =
      Pattern.compile(
          "application/pkcs7-mime\\s*;.*smime-type=(authenticated-enveloped|signed)-data.*",
          Pattern.DOTALL);

  private static final String TRANSFER_ENCODING_7_BIT = "7bit";
  public static final String CONTENT_TRANSFER_ENCODING = "content-transfer-encoding";

  @Override
  public void consumeElement(final RbelElement element, final RbelConversionExecutor context) {
    Optional.ofNullable(element.getParentNode())
        .filter(parent -> isPossibleMimeElementOf(element, parent))
        .map(facet -> element.getContent().toInputStream())
        .ifPresent(
            content ->
                new Parser(context).buildMimeMessageFacet(element, parseMimeMessage(content)));
  }

  private boolean isPossibleMimeElementOf(RbelElement element, RbelElement parent) {
    return parent
            .getFacet(RbelPop3ResponseFacet.class)
            .map(RbelPop3ResponseFacet::getBody)
            .filter(element::equals)
            .isPresent()
        || parent
            .getFacet(RbelSmtpCommandFacet.class)
            .map(RbelSmtpCommandFacet::getBody)
            .filter(element::equals)
            .isPresent()
        || parent
            .getFacet(RbelCmsEnvelopedDataFacet.class)
            .map(RbelCmsEnvelopedDataFacet::getDecrypted)
            .filter(element::equals)
            .isPresent()
        || parent.hasFacet(RbelMimeBodyFacet.class);
  }

  private record Parser(RbelConversionExecutor context) {

    @SneakyThrows
    private void buildMimeMessageFacet(RbelElement element, Entity message) {
      if (message.getHeader().iterator().hasNext()
          || !(message.getBody() instanceof SingleBody singleBody)
          || singleBody.size() > 0) {
        parseEntity(element, message);
      }
    }

    private RbelElement parseEntity(RbelElement element, Entity message) {
      RbelMimeMessageFacet messageFacet = buildMessageFacet(element, message);
      element.addFacet(messageFacet);
      element.addFacet(new RbelRootFacet<>(messageFacet));
      messageFacet
          .header()
          .getFacet(RbelMimeHeaderFacet.class)
          .map(header -> header.get("content-type"))
          .map(RbelElement::getRawStringContent)
          .map(AUTHENTICATED_ENVELOPED_OR_SIGNED_DATA::matcher)
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
      RbelElement headerElement =
          new RbelElement(messageHeader.toString().getBytes(StandardCharsets.UTF_8), element);
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

      int bodyPosition = element.getContent().indexOf(bytes);
      if (bodyPosition == -1) {
        return context.convertElement(
            createBodyElementAndFacet(element, RbelContent.of(bytes), content));
      } else {
        return context.convertElement(
            createBodyElementAndFacet(
                element,
                element.getContent().subArray(bodyPosition, bodyPosition + bytes.length),
                content));
      }
    }

    private RbelElement createBodyElementAndFacet(
        RbelElement element, RbelContent bytes, Supplier<String> content) {
      var bodyFacet = new RbelMimeBodyFacet(content);

      return RbelElement.builder()
          .content(bytes)
          .parentNode(element)
          .build()
          .addFacet(bodyFacet)
          .addFacet(new RbelRootFacet<>(bodyFacet));
    }

    private Pair<byte[], Supplier<String>> extractContent(SingleBody singleBody) {
      if (singleBody instanceof TextBody textBody) {
        Supplier<String> content = () -> readContent(textBody);
        return Pair.of(content.get().getBytes(), content);
      } else {
        Supplier<byte[]> bytes = () -> readBytes(singleBody);
        Supplier<String> content =
            () ->
                Optional.ofNullable(
                        singleBody.getParent().getHeader().getField(CONTENT_TRANSFER_ENCODING))
                    .map(Field::getBody)
                    .filter(TRANSFER_ENCODING_7_BIT::equals)
                    .map(encoding -> new String(bytes.get()))
                    .orElseGet(() -> Base64.getEncoder().encodeToString(bytes.get()));
        return Pair.of(bytes.get(), content);
      }
    }

    @SneakyThrows
    private static String readContent(TextBody textBody) {
      String content;
      try (var reader = textBody.getReader();
          var writer = new StringWriter()) {
        reader.transferTo(writer);
        content = writer.toString();
      }
      return content;
    }

    @SneakyThrows
    private static byte[] readBytes(SingleBody singleBody) {
      try (var in = singleBody.getInputStream()) {
        return ByteArrayUtils.getBytesFrom(in::transferTo);
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
          .map(part -> parseEntity(new RbelElement(element), part))
          .forEach(parts::add);
      return element.addFacet(new RbelListFacet(parts));
    }
  }

  @SneakyThrows
  public static Message parseMimeMessage(InputStream input) {
    final DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
    messageBuilder.setMimeEntityConfig(new MimeConfig.Builder().setMaxLineLen(10_000).build());
    return messageBuilder.parseMessage(input);
  }

  private static RbelElement createChildNode(RbelElement element) {
    return new RbelElement(null, element.getContent(), element, Optional.empty());
  }

  private static RbelElement buildElementIfPresent(RbelElement element, String value) {
    return Optional.ofNullable(value).map(s -> RbelElement.wrap(element, s)).orElse(null);
  }
}
