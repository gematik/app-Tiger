/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.util.IRbelMessageListener;
import de.gematik.test.tiger.common.config.RbelModificationDescription;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import java.security.Key;
import java.util.List;

public interface ITigerProxy {

  TigerProxyRoute addRoute(TigerProxyRoute tigerRoute);

  void removeRoute(String routeId);

  void addRbelMessageListener(IRbelMessageListener listener);

  void removeRbelMessageListener(IRbelMessageListener listener);

  String getBaseUrl();

  int getProxyPort();

  void addKey(String keyid, Key key);

  List<TigerProxyRoute> getRoutes();

  void clearAllRoutes();

  RbelModificationDescription addModificaton(RbelModificationDescription modification);

  List<RbelModificationDescription> getModifications();

  void removeModification(String modificationId);
}
