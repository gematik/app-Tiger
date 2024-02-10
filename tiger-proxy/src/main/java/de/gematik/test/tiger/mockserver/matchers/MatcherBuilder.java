/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.matchers;

import static java.util.concurrent.TimeUnit.MINUTES;

import de.gematik.test.tiger.mockserver.cache.LRUCache;
import de.gematik.test.tiger.mockserver.configuration.Configuration;
import de.gematik.test.tiger.mockserver.mock.Expectation;
import de.gematik.test.tiger.mockserver.model.RequestDefinition;

/*
 * @author jamesdbloom
 */
public class MatcherBuilder {

  private final Configuration configuration;
  private final LRUCache<RequestDefinition, HttpRequestMatcher> requestMatcherLRUCache;

  public MatcherBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.requestMatcherLRUCache = new LRUCache<>(250, MINUTES.toMillis(10));
  }

  public HttpRequestMatcher transformsToMatcher(RequestDefinition requestDefinition) {
    HttpRequestMatcher httpRequestMatcher = requestMatcherLRUCache.get(requestDefinition);
    if (httpRequestMatcher == null) {
      httpRequestMatcher = new HttpRequestPropertiesMatcher(configuration);
      httpRequestMatcher.update(requestDefinition);
      requestMatcherLRUCache.put(requestDefinition, httpRequestMatcher);
    }
    return httpRequestMatcher;
  }

  public HttpRequestMatcher transformsToMatcher(Expectation expectation) {
    HttpRequestMatcher httpRequestMatcher = new HttpRequestPropertiesMatcher(configuration);
    httpRequestMatcher.update(expectation);
    return httpRequestMatcher;
  }
}
