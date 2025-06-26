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
package de.gematik.test.tiger.mockserver.model;

import de.gematik.rbellogger.data.RbelElement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.Data;

/*
 * @author jamesdbloom
 */
@SuppressWarnings("rawtypes")
@Data
public abstract class HttpMessage<T extends HttpMessage> extends ObjectWithJsonToString
    implements Message {
  private byte[] body = null;

  private CompletableFuture<RbelElement> parsedMessageFuture;

  public abstract T withBody(byte[] body);

  public abstract Headers getHeaders();

  public abstract T withHeaders(Headers headers);

  public abstract T withHeader(Header header);

  public abstract T withHeader(String name, String... values);

  public abstract T replaceHeader(Header header);

  public abstract List<Header> getHeaderList();

  public abstract List<String> getHeader(String name);

  public abstract String getFirstHeader(String name);

  public abstract T removeHeader(String name);
}
