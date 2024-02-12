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

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode
@Getter
public class HttpOverrideForwardedRequest extends Action<HttpOverrideForwardedRequest> {
  @JsonAlias("httpRequest")
  private HttpRequest requestOverride;

  @JsonAlias("httpResponse")
  private HttpResponse responseOverride;

  /** Static builder which will allow overriding proxied request with the specified request. */
  public static HttpOverrideForwardedRequest forwardOverriddenRequest() {
    return new HttpOverrideForwardedRequest();
  }

  /**
   * Static builder which will allow overriding proxied request with the specified request.
   *
   * @param httpRequest the HttpRequest specifying what to override
   */
  public static HttpOverrideForwardedRequest forwardOverriddenRequest(HttpRequest httpRequest) {
    return new HttpOverrideForwardedRequest().withRequestOverride(httpRequest);
  }

  /**
   * Static builder which will allow overriding proxied request with the specified request.
   *
   * @param httpRequest the HttpRequest specifying what to override
   * @param httpResponse the HttpRequest specifying what to override
   */
  public static HttpOverrideForwardedRequest forwardOverriddenRequest(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return new HttpOverrideForwardedRequest()
        .withRequestOverride(httpRequest)
        .withResponseOverride(httpResponse);
  }

  /**
   * All fields, headers, cookies, etc of the provided request will be overridden
   *
   * @param httpRequest the HttpRequest specifying what to override
   */
  public HttpOverrideForwardedRequest withRequestOverride(HttpRequest httpRequest) {
    this.requestOverride = httpRequest;
    return this;
  }

  /**
   * All fields, headers, cookies, etc of the provided response will be overridden
   *
   * @param httpResponse the HttpResponse specifying what to override
   */
  public HttpOverrideForwardedRequest withResponseOverride(HttpResponse httpResponse) {
    this.responseOverride = httpResponse;
    return this;
  }

  @Override
  @JsonIgnore
  public Type getType() {
    return Type.FORWARD_REPLACE;
  }
}
