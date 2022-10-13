/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.env;

import de.gematik.test.tiger.testenvmgr.data.BannerType;
import java.util.LinkedHashMap;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerStatusUpdate {
    static long lastIndex;
    static final Object indexMutex = new Object();


    public TigerStatusUpdate(long dummyIndexForJackson, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate, String bannerMessage, String bannerColor, BannerType bannerType, boolean isHtml) {
        this.featureMap = Objects.requireNonNullElseGet(featureMap, LinkedHashMap::new);
        this.serverUpdate = Objects.requireNonNullElseGet(serverUpdate, LinkedHashMap::new);
        this.bannerMessage = bannerMessage;
        this.bannerColor = bannerColor;
        this.bannerIsHtml = isHtml;

        this.bannerType = Objects.requireNonNullElse(bannerType, BannerType.MESSAGE);
        synchronized (indexMutex) {
            index = lastIndex++;
        }
    }

    public TigerStatusUpdate(long dummyIndexForJackson, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate, String bannerMessage, String bannerColor, BannerType bannerType) {
        this(dummyIndexForJackson, featureMap, serverUpdate, bannerMessage, bannerColor, bannerType, false);
    }

    private long index;
    private LinkedHashMap<String, FeatureUpdate> featureMap;
    private LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate;

    private String bannerMessage;
    private String bannerColor;
    private BannerType bannerType;

    private boolean bannerIsHtml;
}
