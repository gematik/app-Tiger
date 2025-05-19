package de.gematik.test.tiger.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import lombok.val;
import org.apache.commons.io.FileUtils;

public class CurlTestdataUtil {

  public static String readCurlFromFileWithCorrectedLineBreaks(String fileName) {
    try {
      String fromFile = FileUtils.readFileToString(new File(fileName), Charset.defaultCharset());
      val messageParts = fromFile.split("(\r\n\r\n)|(\n\n)|(\r\r)", 2);
      messageParts[0] = messageParts[0].replaceAll("(?<!\\r)\\n", "\r\n");
      if (!messageParts[1].endsWith("\r\n")) {
        messageParts[1] += "\r\n";
      }
      if (!messageParts[0].contains("\r\nContent-Length: ")) {
        fromFile =
            messageParts[0]
                + "\r\nContent-Length: "
                + (messageParts[1].length() + 2)
                + "\r\n\r\n"
                + messageParts[1];
      }
      return fromFile;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
