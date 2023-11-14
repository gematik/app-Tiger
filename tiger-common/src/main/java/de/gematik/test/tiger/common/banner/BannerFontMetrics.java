/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.banner;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BannerFontMetrics {
  private int width;
  private int height;
  private boolean upperCaseOnly;
}
