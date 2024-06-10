/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/*
 * @author jamesdbloom
 */
@EqualsAndHashCode(callSuper = false)
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
