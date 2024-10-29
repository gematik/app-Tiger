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

import static de.gematik.test.tiger.mockserver.model.HttpStatusCode.NOT_FOUND_404;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Multimap;
import de.gematik.test.tiger.mockserver.netty.responsewriter.NettyResponseWriter;
import java.util.Collections;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
@Data
public class HttpResponse extends HttpMessage<HttpResponse> implements Action {
  private int hashCode;
  private Integer statusCode;
  private String reasonPhrase;
  private Headers headers;
  private Integer streamId = null;
  private String expectationId;

  /** Static builder to create a response. */
  public static HttpResponse response() {
    return new HttpResponse();
  }

  /** Static builder to create a not found response. */
  public static HttpResponse notFoundResponse() {
    return new HttpResponse()
        .withStatusCode(NOT_FOUND_404.code())
        .withReasonPhrase(NOT_FOUND_404.reasonPhrase());
  }

  /**
   * The status code to return, such as 200, 404, the status code specified here will result in the
   * default status message for this status code for example for 200 the status message "OK" is used
   *
   * @param statusCode an integer such as 200 or 404
   */
  public HttpResponse withStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
    this.hashCode = 0;
    return this;
  }

  /**
   * The reason phrase to return, if no reason code is returned this will be defaulted to the
   * standard reason phrase for the statusCode, i.e. for a statusCode of 200 the standard reason
   * phrase is "OK"
   *
   * @param reasonPhrase an string such as "Not Found" or "OK"
   */
  public HttpResponse withReasonPhrase(String reasonPhrase) {
    this.reasonPhrase = reasonPhrase;
    this.hashCode = 0;
    return this;
  }

  private Headers getOrCreateHeaders() {
    if (this.headers == null) {
      this.headers = new Headers();
      this.hashCode = 0;
    }
    return this.headers;
  }

  @Override
  public HttpResponse withBody(byte[] body) {
    setBody(body);
    return this;
  }

  public HttpResponse withHeaders(Headers headers) {
    if (headers == null || headers.isEmpty()) {
      this.headers = null;
    } else {
      this.headers = headers;
    }
    this.hashCode = 0;
    return this;
  }

  /**
   * Add a header to return as a Header object, if a header with the same name already exists this
   * will NOT be modified but two headers will exist
   *
   * @param header a Header object
   */
  public HttpResponse withHeader(Header header) {
    getOrCreateHeaders().withEntry(header);
    this.hashCode = 0;
    return this;
  }

  /**
   * Add a header to return as a Header object, if a header with the same name already exists this
   * will NOT be modified but two headers will exist
   *
   * @param name the header name
   * @param values the header values
   */
  public HttpResponse withHeader(String name, String... values) {
    if (values.length == 0) {
      values = new String[] {".*"};
    }
    getOrCreateHeaders().withEntry(name, values);
    this.hashCode = 0;
    return this;
  }

  /**
   * Update header to return as a Header object, if a header with the same name already exists it
   * will be modified
   *
   * @param header a Header object
   */
  public HttpResponse replaceHeader(Header header) {
    getOrCreateHeaders().replaceEntry(header);
    this.hashCode = 0;
    return this;
  }

  public List<Header> getHeaderList() {
    if (this.headers != null) {
      return this.headers.getEntries();
    } else {
      return Collections.emptyList();
    }
  }

  public List<String> getHeader(String name) {
    if (this.headers != null) {
      return this.headers.getValues(name);
    } else {
      return Collections.emptyList();
    }
  }

  public String getFirstHeader(String name) {
    if (this.headers != null) {
      return this.headers.getFirstValue(name);
    } else {
      return "";
    }
  }

  public HttpResponse removeHeader(String name) {
    if (this.headers != null) {
      headers.remove(name);
      this.hashCode = 0;
    }
    return this;
  }

  /**
   * Returns true if a header with the specified name has been added
   *
   * @param name the header name
   * @param value the header value
   * @return true if a header has been added with that name otherwise false
   */
  public boolean containsHeader(String name, String value) {
    if (this.headers != null) {
      return this.headers.containsEntry(name, value);
    } else {
      return false;
    }
  }

  public HttpResponse withStreamId(Integer streamId) {
    this.streamId = streamId;
    this.hashCode = 0;
    return this;
  }

  @Override
  @JsonIgnore
  public Type getType() {
    return Type.RESPONSE;
  }

  @Override
  public void write(NettyResponseWriter nettyResponseWriter, HttpRequest request) {
    nettyResponseWriter.writeHttpResponse(request, this);
  }

  public Multimap<String, String> getHeaderMultimap() {
    return headers.getMultimap();
  }
}
