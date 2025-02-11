/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.proxy.handler.TigerExceptionUtils;
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
      BannerDetails bannerDetails,
      boolean isHtml) {
    this.featureMap = featureMap == null ? new LinkedHashMap<>() : new LinkedHashMap<>(featureMap);
    this.serverUpdate =
        serverUpdate == null ? new LinkedHashMap<>() : new LinkedHashMap<>(serverUpdate);
    this.bannerMessage = bannerMessage;
    this.bannerColor = bannerColor;
    this.bannerIsHtml = isHtml;
    this.bannerDetails = bannerDetails;
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
      BannerDetails bannerDetails,
      BannerType bannerType) {
    this(
        dummyIndexForJackson,
        featureMap,
        serverUpdate,
        bannerMessage,
        bannerColor,
        bannerType,
        bannerDetails,
        false);
  }

  private long index;
  private LinkedHashMap<String, FeatureUpdate> featureMap;
  private LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate;

  private String bannerMessage;
  private String bannerColor;
  private BannerType bannerType;
  private BannerDetails bannerDetails;

  private boolean bannerIsHtml;

  @Data
  public static class BannerDetails {
    private final String detailedMessage;
    private String stackTrace;
    private String exceptionClassName;

    public BannerDetails(Exception exception) {
      this.detailedMessage = exception.getMessage();
      this.exceptionClassName = exception.getClass().getName();
      this.stackTrace = TigerExceptionUtils.getStackTraceAsString(exception);
    }

    public BannerDetails(String detailedMessage) {
      this.detailedMessage = detailedMessage;
    }
  }
}
