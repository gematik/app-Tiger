package de.gematik.test.tiger.mockserver.model;

import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;

public class Headers extends KeysToMultiValues<Header, Headers> {

  public Headers(List<Header> headers) {
    withEntries(headers);
  }

  public Headers(Header... headers) {
    withEntries(headers);
  }

  public Headers(Multimap<String, String> headers) {
    super(headers);
  }

  public static Headers headers(Header... headers) {
    return new Headers(headers);
  }

  @Override
  public Header build(String name, Collection<String> values) {
    return new Header(name, values);
  }

  protected void isModified() {}

  public Headers withKeyMatchStyle(KeyMatchStyle keyMatchStyle) {
    super.withKeyMatchStyle(keyMatchStyle);
    return this;
  }

  public Headers clone() {
    return new Headers(getMultimap());
  }
}
