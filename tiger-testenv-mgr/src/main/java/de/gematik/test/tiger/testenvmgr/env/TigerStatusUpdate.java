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

import java.util.LinkedHashMap;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TigerStatusUpdate {
    static long lastIndex;
    static Object indexMutex = new Object();


    public TigerStatusUpdate(long dummy, LinkedHashMap<String, FeatureUpdate> featureMap, LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate, String bannerMessage, String bannerColor) {
        this.featureMap = featureMap;
        this.serverUpdate = serverUpdate;
        this.bannerMessage = bannerMessage;
        this.bannerColor = bannerColor;
        synchronized (indexMutex) {
            index = lastIndex++;
        }
    }

    private long index;
    private LinkedHashMap<String, FeatureUpdate> featureMap;
    private LinkedHashMap<String, TigerServerStatusUpdate> serverUpdate;

    private String bannerMessage;
    private String bannerColor;
}
