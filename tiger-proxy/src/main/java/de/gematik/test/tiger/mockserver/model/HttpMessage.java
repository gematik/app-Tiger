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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.nio.charset.Charset;
import java.util.List;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
public interface HttpMessage<T extends HttpMessage, B extends Body> extends Message {

  T withBody(String body);

  T withBody(String body, Charset charset);

  T withBody(byte[] body);

  T withBody(B body);

  B getBody();

  @JsonIgnore
  byte[] getBodyAsRawBytes();

  @JsonIgnore
  String getBodyAsString();

  Headers getHeaders();

  T withHeaders(Headers headers);

  T withHeader(Header header);

  T withHeader(String name, String... values);

  T replaceHeader(Header header);

  List<Header> getHeaderList();

  List<String> getHeader(String name);

  String getFirstHeader(String name);

  T removeHeader(String name);

  Cookies getCookies();

  T withCookies(Cookies cookies);

  List<Cookie> getCookieList();
}
