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

package de.gematik.test.tiger.mockserver.model;

import static org.apache.commons.lang3.StringUtils.*;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("unused")
@Slf4j
public class MediaType extends ObjectWithJsonToString {

  /**
   * The default character set for an HTTP message, if none is specified in the Content-Type header.
   * From the HTTP 1.1 specification section 3.7.1
   * (http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7.1):
   *
   * <pre>
   *     The "charset" parameter is used with some media types to define the character set (section 3.4) of the data.
   *     When no explicit charset parameter is provided by the sender, media subtypes of the "text" type are defined to
   *     have a default charset value of "ISO-8859-1" when received via HTTP. Data in character sets other than
   *     "ISO-8859-1" or its subsets MUST be labeled with an appropriate charset value.
   * </pre>
   */
  public static final Charset DEFAULT_TEXT_HTTP_CHARACTER_SET = StandardCharsets.ISO_8859_1;

  /**
   * JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded
   * using UTF-8 [RFC3629]. (https://datatracker.ietf.org/doc/html/rfc8259#section-8.1)
   */
  public static final Charset DEFAULT_JSON_HTTP_CHARACTER_SET = StandardCharsets.UTF_8;

  private static final char TYPE_SEPARATOR = '/';
  private static final char PARAMETER_START = ';';
  private final String type;
  private final String subtype;
  private final Map<String, String> parameters;
  private final Charset charset;
  private final String toString;
  private final boolean isBlank;

  private static final String CHARSET_PARAMETER = "charset";
  private static final String MEDIA_TYPE_WILDCARD = "*";
  public static final MediaType WILDCARD = new MediaType(MEDIA_TYPE_WILDCARD, MEDIA_TYPE_WILDCARD);
  public static final MediaType APPLICATION_ATOM_XML = new MediaType("application", "atom+xml");
  public static final MediaType APPLICATION_XHTML_XML = new MediaType("application", "xhtml+xml");
  public static final MediaType APPLICATION_SVG_XML = new MediaType("application", "svg+xml");
  public static final MediaType APPLICATION_XML = new MediaType("application", "xml");
  public static final MediaType APPLICATION_XML_UTF_8 =
      new MediaType("application", "xml", "utf-8", null);
  public static final MediaType APPLICATION_JSON = new MediaType("application", "json");
  public static final MediaType APPLICATION_JSON_UTF_8 =
      new MediaType("application", "json", "utf-8", null);
  public static final MediaType JSON_UTF_8 = APPLICATION_JSON_UTF_8;
  public static final MediaType APPLICATION_FORM_URLENCODED =
      new MediaType("application", "x-www-form-urlencoded");
  public static final MediaType FORM_DATA = new MediaType("application", "x-www-form-urlencoded");
  public static final MediaType MULTIPART_FORM_DATA = new MediaType("multipart", "form-data");
  public static final MediaType APPLICATION_OCTET_STREAM =
      new MediaType("application", "octet-stream");
  public static final MediaType APPLICATION_BINARY = new MediaType("application", "binary");
  public static final MediaType PDF = new MediaType("application", "pdf");
  public static final MediaType ATOM_UTF_8 =
      new MediaType("application", "atom+xml", "utf-8", null);
  public static final MediaType TEXT_PLAIN = new MediaType("text", "plain");
  public static final MediaType PLAIN_TEXT_UTF_8 = new MediaType("text", "plain", "utf-8", null);
  public static final MediaType TEXT_XML = new MediaType("text", "xml");
  public static final MediaType TEXT_XML_UTF_8 = new MediaType("text", "xml", "utf-8", null);
  public static final MediaType XML_UTF_8 = TEXT_XML_UTF_8;
  public static final MediaType TEXT_HTML = new MediaType("text", "html");
  public static final MediaType TEXT_HTML_UTF_8 = new MediaType("text", "html", "utf-8", null);
  public static final MediaType HTML_UTF_8 = TEXT_HTML_UTF_8;
  public static final MediaType SERVER_SENT_EVENTS = new MediaType("text", "event-stream");
  public static final MediaType APPLICATION_JSON_PATCH_JSON =
      new MediaType("application", "json-patch+json");
  public static final MediaType ANY_VIDEO_TYPE = new MediaType("video", MEDIA_TYPE_WILDCARD);
  public static final MediaType ANY_AUDIO_TYPE = new MediaType("audio", MEDIA_TYPE_WILDCARD);
  public static final MediaType ANY_IMAGE_TYPE = new MediaType("image", MEDIA_TYPE_WILDCARD);
  public static final MediaType QUICKTIME = new MediaType("video", "quicktime");
  public static final MediaType JPEG = new MediaType("image", "jpeg");
  public static final MediaType PNG = new MediaType("image", "png");

  public static MediaType parse(String mediaTypeHeader) {
    if (isNotBlank(mediaTypeHeader)) {
      int typeSeparator = mediaTypeHeader.indexOf(TYPE_SEPARATOR);
      int typeEndIndex = 0;
      String type = null;
      String subType = null;
      if (typeSeparator != -1) {
        typeEndIndex = mediaTypeHeader.indexOf(PARAMETER_START);
        if (typeEndIndex == -1) {
          typeEndIndex = mediaTypeHeader.length();
        }
        String typeString = mediaTypeHeader.substring(0, typeEndIndex).trim();
        type = substringBefore(typeString, "/").trim().toLowerCase();
        subType = substringAfter(typeString, "/").trim().toLowerCase();
        if (typeEndIndex < mediaTypeHeader.length()) {
          typeEndIndex++;
        }
      }
      String parameters =
          mediaTypeHeader.substring(typeEndIndex).trim().toLowerCase().replaceAll("\"", "");
      Map<String, String> parameterMap = new ConcurrentHashMap<>();
      if (isNotBlank(parameters)) {
        try {
          for (String parameter : Splitter.on(';').split(parameters)) {
            String parameterTrimmed = parameter.trim();
            String key = substringBefore(parameterTrimmed, "=").trim();
            String value = substringAfter(parameterTrimmed, "=").trim();
            if (isNotBlank(key) && isNotBlank(value)) {
              parameterMap.put(key, value);
            }
          }
          if (parameterMap.size() > 1) {
            // sort if multiple entries to ensure equals and hashcode is consistent
            parameterMap =
                parameterMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(
                        Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (oldValue, newValue) -> oldValue,
                            LinkedHashMap::new));
          }
        } catch (RuntimeException throwable) {
          log.warn(
              "invalid parameters format \"" + parameters + "\", expected{}see:{}",
              "Content-Type := type \"/\" subtype *[\";\" parameter]\n"
                  + "parameter := attribute \"=\" value",
              "https://www.w3.org/Protocols/rfc1341/4_Content-Type.html",
              throwable);
        }
      }
      return new MediaType(type, subType, parameterMap);
    } else {
      return new MediaType(null, null);
    }
  }

  private static TreeMap<String, String> createParametersMap(Map<String, String> initialValues) {
    final TreeMap<String, String> map = new TreeMap<>(String::compareToIgnoreCase);
    if (initialValues != null) {
      for (Map.Entry<String, String> entry : initialValues.entrySet()) {
        map.put(entry.getKey().toLowerCase().trim(), entry.getValue().trim());
      }
    }
    return map;
  }

  public MediaType(String type, String subtype) {
    this(type, subtype, null, null);
  }

  public MediaType(String type, String subtype, Map<String, String> parameters) {
    this(type, subtype, null, parameters);
  }

  private MediaType(String type, String subtype, String charset, Map<String, String> parameterMap) {
    this.type = isBlank(type) ? null : type;
    this.subtype = isBlank(subtype) ? null : subtype;
    this.parameters = new TreeMap<>(String::compareToIgnoreCase);
    if (parameterMap != null) {
      parameterMap.forEach((key, value) -> this.parameters.put(key.toLowerCase(), value));
    }
    Charset parsedCharset = null;
    if (isNotBlank(charset)) {
      this.parameters.put(CHARSET_PARAMETER, charset);
      try {
        parsedCharset = Charset.forName(charset);
      } catch (RuntimeException throwable) {
        log.debug("ignoring unsupported charset with value \"{}\"", charset, throwable);
      }
    } else {
      try {
        if (parameters.containsKey(CHARSET_PARAMETER)) {
          parsedCharset = Charset.forName(parameters.get(CHARSET_PARAMETER));
        }
      } catch (RuntimeException throwable) {
        log.debug("ignoring unsupported charset with value \"{}\"", charset, throwable);
      }
    }
    this.charset = parsedCharset;
    this.toString = initialiseToString();
    this.isBlank = isBlank(this.toString);
  }

  private String initialiseToString() {
    StringBuilder stringBuilder = new StringBuilder();
    if (type != null && subtype != null) {
      stringBuilder.append(type).append(TYPE_SEPARATOR).append(subtype);
    }
    if (!parameters.isEmpty()) {
      if (stringBuilder.length() > 0) {
        stringBuilder.append(PARAMETER_START).append(' ');
      }
      stringBuilder.append(Joiner.on("; ").withKeyValueSeparator("=").join(parameters));
    }
    return stringBuilder.toString();
  }

  public static MediaType create(String type, String subType) {
    return new MediaType(type, subType);
  }

  public String getType() {
    return this.type;
  }

  public String getSubtype() {
    return this.subtype;
  }

  public Map<String, String> getParameters() {
    return parameters;
  }

  public MediaType withCharset(Charset charset) {
    return withCharset(charset.name());
  }

  public MediaType withCharset(String charset) {
    return new MediaType(this.type, this.subtype, charset.toLowerCase(), this.parameters);
  }

  public Charset getCharset() {
    return charset;
  }

  public Charset getCharsetOrDefault() {
    if (charset != null) {
      return charset;
    } else if (isBlank || isJson() || isXml()) {
      return DEFAULT_JSON_HTTP_CHARACTER_SET;
    } else if (isString()) {
      return DEFAULT_TEXT_HTTP_CHARACTER_SET;
    } else {
      return null;
    }
  }

  public boolean isCompatible(MediaType other) {
    // return false if other is null, else
    return other != null
        &&
        // both are wildcard types, or
        (type == null
            || other.type == null
            || type.equals(MEDIA_TYPE_WILDCARD)
            || other.type.equals(MEDIA_TYPE_WILDCARD)
            ||
            // same types, wildcard sub-types, or
            (type.equalsIgnoreCase(other.type) && (subtype == null || other.subtype == null))
            || (type.equalsIgnoreCase(other.type)
                && (subtype.equals(MEDIA_TYPE_WILDCARD)
                    || other.subtype.equals(MEDIA_TYPE_WILDCARD)))
            ||
            // same types & sub-types
            (type.equalsIgnoreCase(other.type) && this.subtype.equalsIgnoreCase(other.subtype)));
  }

  public boolean isJson() {
    return !isBlank
        && contentTypeContains(new String[] {"json"})
        && !contentTypeContains(new String[] {"ndjson"});
  }

  public boolean isXml() {
    return !isBlank && contentTypeContains(new String[] {"xml"});
  }

  public boolean isString() {
    return isBlank
        || contentTypeContains(
            new String[] {
              "utf-8",
              "utf8",
              "text",
              "json",
              "css",
              "html",
              "xhtml",
              "form",
              "javascript",
              "ecmascript",
              "xml",
              "wsdl",
              "csv",
              "urlencoded"
            });
  }

  private boolean contentTypeContains(String[] subStrings) {
    String contentType = toString().toLowerCase();
    for (String subString : subStrings) {
      if (contentType.contains(subString)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return toString;
  }
}
