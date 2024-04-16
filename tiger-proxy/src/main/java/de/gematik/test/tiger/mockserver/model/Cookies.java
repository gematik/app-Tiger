/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import java.util.Map;

/*
 * @author jamesdbloom
 */
public class Cookies extends KeysAndValues<Cookie, Cookies> {

  public Cookies(Cookie... cookies) {
    withEntries(cookies);
  }

  public Cookies(Map<String, String> cookies) {
    super(cookies);
  }

  @Override
  public Cookie build(String name, String value) {
    return new Cookie(name, value);
  }

  public Cookies clone() {
    return new Cookies(getMap());
  }
}
