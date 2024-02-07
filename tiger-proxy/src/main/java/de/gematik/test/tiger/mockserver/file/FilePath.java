/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.file;

import java.io.File;
import java.net.URL;

/*
 * @author jamesdbloom
 */
public class FilePath {
  public static String absolutePathFromClassPathOrPath(String filename) {
    URL resource = FilePath.class.getClassLoader().getResource(filename);
    if (resource != null) {
      return resource.getPath();
    }
    return new File(filename).getAbsolutePath();
  }
}
