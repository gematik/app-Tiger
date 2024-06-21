package de.gematik.rbellogger.converter.http;

import java.nio.charset.Charset;

public interface RbelHttpCodingConverter {
  byte[] decode(byte[] bytes, String eol, Charset charset);
}
