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

package de.gematik.test.tiger.proxy;


import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.modifier.RbelModificationDescription;
import de.gematik.test.tiger.common.config.tigerProxy.TigerRoute;

import java.security.Key;
import java.util.List;

public interface ITigerProxy {

    TigerRoute addRoute(TigerRoute tigerRoute);

    void removeRoute(String routeId);

    void addRbelMessageListener(IRbelMessageListener listener);

    void removeRbelMessageListener(IRbelMessageListener listener);

    String getBaseUrl();

    int getPort();

    List<RbelElement> getRbelMessages();

    void addKey(String keyid, Key key);

    List<TigerRoute> getRoutes();

    void clearAllRoutes();

    RbelModificationDescription addModificaton(RbelModificationDescription modification);

    List<RbelModificationDescription> getModifications();

    void removeModification(String modificationId);
}
