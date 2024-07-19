/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.exceptions.RbelConversionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
          "application/pkcs7-mime\\s*;\\s*smime-type=authenticated-enveloped-data.*",
          Pattern.DOTALL);

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
          .filter(content -> AUTHENTICATED_ENVELOPED_DATA.matcher(content).matches())
          .ifPresent(content -> context.convertElement(messageFacet.body()));
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
      RbelElement bodyElement;
      RbelMimeBodyFacet bodyFacet;
      if (singleBody instanceof TextBody textBody) {
        try (var reader = textBody.getReader();
            var writer = new StringWriter()) {
          reader.transferTo(writer);
          var content = writer.toString();
          bodyElement = new RbelElement(content.getBytes(), element);
          bodyFacet = new RbelMimeBodyFacet(content);
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
          var base64 = Base64.getEncoder().encodeToString(out.getBytes());
          bodyElement = new RbelElement(out.getBytes(), element);
          bodyFacet = new RbelMimeBodyFacet(base64);
        }
      }
      bodyElement.addFacet(bodyFacet);
      bodyElement.addFacet(new RbelRootFacet<>(bodyFacet));
      return bodyElement;
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
