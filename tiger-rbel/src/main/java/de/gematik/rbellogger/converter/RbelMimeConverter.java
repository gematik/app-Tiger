/*
 * Copyright (c) 2024 gematik GmbH
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
import de.gematik.rbellogger.exceptions.RbelConversionException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.*;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.Field;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Optional;

@Slf4j
public class RbelMimeConverter implements RbelConverterPlugin {

  @Override
  public void consumeElement(final RbelElement element, final RbelConverter context) {
    Optional.ofNullable(element.getParentNode())
        .flatMap(parent -> parent.getFacet(RbelPop3ResponseFacet.class))
        .filter(facet -> facet.getBody() == element)
        .flatMap(facet ->
            Optional.ofNullable(element.getRawContent()))
        .ifPresent(content -> parseEntity(element, parseMimeMessage(content)));
  }

  @SneakyThrows
  private Message parseMimeMessage(byte[] bytes) {
    return new DefaultMessageBuilder().parseMessage(new ByteArrayInputStream(bytes));
  }

  private RbelElement parseEntity(RbelElement element, Entity message) {
    return element.addFacet(buildMessageFacet(element, message));
  }

  @SneakyThrows
  private RbelMimeMessageFacet buildMessageFacet(RbelElement element, Entity message) {
    var body = parseBody(element, message.getBody());
    var header = parseHeader(element, message.getHeader());
    return RbelMimeMessageFacet.builder().header(header).body(body).build();
  }

  private RbelElement parseHeader(RbelElement element, Header messageHeader) {
    var headerFacet = new RbelMimeHeaderFacet();
    messageHeader
        .getFieldsAsMap()
        .forEach(
            (name, values) ->
                values.stream()
                    .map(Field::getBody)
                    .map(value -> RbelElement.wrap(element, value))
                    .forEach(valueElement -> headerFacet.put(name, valueElement)));
    return RbelElement.wrap(element, messageHeader.toString()).addFacet(headerFacet);
  }

  @SneakyThrows
  private RbelElement parseBody(RbelElement element, Body body) {
    if (body instanceof Multipart multipart) {
      return parseMultiPartBody(element, multipart);
    } else if (body instanceof SingleBody singleBody) {
      return parseSingleBody(element, singleBody);
    } else {
      throw new RbelConversionException("unknown message body type: " + body.getClass().getName());
    }
  }

  @SneakyThrows
  private static RbelElement parseSingleBody(RbelElement element, SingleBody singleBody) {
    if (singleBody instanceof TextBody textBody) {
      try (var reader = textBody.getReader();
          var writer = new StringWriter()) {
        reader.transferTo(writer);
        return RbelElement.wrap(element, writer);
      }
    }
    try (var in = singleBody.getInputStream();
        var out =
            new ByteArrayOutputStream() {
              public byte[] getBytes() {
                return buf;
              }
            }) {
      in.transferTo(out);
      return new RbelElement(out.getBytes(), element);
    }
  }

  private RbelElement parseMultiPartBody(RbelElement parentElement, Multipart multipart) {
    var element = createChildNode(parentElement);
    var parts = parseMultipart(element, multipart);
    var preamble = buildElementIfPresent(element, multipart.getPreamble());
    var epilogue = buildElementIfPresent(element, multipart.getEpilogue());
    var bodyFacet =
        RbelMimeMultipartFacet.builder().preamble(preamble).parts(parts).epilogue(epilogue).build();
    return element.addFacet(bodyFacet);
  }

  private RbelElement parseMultipart(RbelElement parentElement, Multipart multipart) {
    var element = createChildNode(parentElement);
    var parts = new ArrayList<RbelElement>(multipart.getCount());
    multipart.getBodyParts().stream()
        .map(part -> parseEntity(createChildNode(element), part))
        .forEach(parts::add);
    return element.addFacet(new RbelListFacet(parts));
  }

  private static RbelElement createChildNode(RbelElement element) {
    return new RbelElement(new byte[0], element);
  }

  private static RbelElement buildElementIfPresent(RbelElement element, String value) {
    return Optional.ofNullable(value).map(s -> RbelElement.wrap(element, s)).orElse(null);
  }
}
