/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.data.BannerType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class TigerStatusUpdate {
  static long lastIndex;
  static final Object indexMutex = new Object();

  @SuppressWarnings("unused")
  public TigerStatusUpdate(
      // for the lastIndex member we need a long param in the ctor too
      // it's unused on purpose as it's set via unique counter approach
      long dummyIndexForJackson,
      Map<String, FeatureUpdate> featureMap,
      Map<String, TigerServerStatusUpdate> serverUpdate,
      String bannerMessage,
      String bannerColor,
      BannerType bannerType,
      boolean isHtml) {
    this.featureMap = featureMap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(featureMap);
    this.serverUpdate =
        serverUpdate == null ? new LinkedHashMap<>() : new LinkedHashMap<>(serverUpdate);
    this.bannerMessage = bannerMessage;
    this.bannerColor = bannerColor;
    this.bannerIsHtml = isHtml;

    this.bannerType = Objects.requireNonNullElse(bannerType, BannerType.MESSAGE);
    synchronized (indexMutex) {
      index = lastIndex++;
    }
  }

  public TigerStatusUpdate(
      long dummyIndexForJackson,
      Map<String, FeatureUpdate> featureMap,
      Map<String, TigerServerStatusUpdate> serverUpdate,
      String bannerMessage,
      String bannerColor,
      BannerType bannerType) {
    this(
        dummyIndexForJackson,
        featureMap,
        serverUpdate,
        bannerMessage,
        bannerColor,
        bannerType,
        false);
  }

  private long index;
  private LinkedHashMap<String, FeatureUpdate> featureMap;
  private LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate;

  private String bannerMessage;
  private String bannerColor;
  private BannerType bannerType;

  private boolean bannerIsHtml;
}
