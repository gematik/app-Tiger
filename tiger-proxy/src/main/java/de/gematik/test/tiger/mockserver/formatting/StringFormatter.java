/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.formatting;

import static de.gematik.test.tiger.mockserver.character.Character.NEW_LINE;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.netty.buffer.ByteBufUtil;

/*
 * @author jamesdbloom
 */
public class StringFormatter {

  private static final Splitter fixedLengthSplitter = Splitter.fixedLength(64);
  private static final Joiner newLineJoiner = Joiner.on(NEW_LINE);

  public static String formatBytes(byte[] bytes) {
    return newLineJoiner.join(fixedLengthSplitter.split(ByteBufUtil.hexDump(bytes)));
  }
}
