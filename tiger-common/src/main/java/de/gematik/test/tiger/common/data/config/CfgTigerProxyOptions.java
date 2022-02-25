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

package de.gematik.test.tiger.common.data.config;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import lombok.Data;

@Data
public class CfgTigerProxyOptions {

    /**
     * Management-port of the Tiger Proxy.
     */
    private int serverPort = -1;

    /**
     * used to overwrite proxyCfg with values that allow to reverse proxy the given server node.
     */
    private String proxiedServer;

    /**
     * Used to add a route to the Tiger Proxy. By default, the healthcheck-url-protocol is used here, or http if none is
     * present. If you want to override this you can do it using this field.
     */
    private String proxiedServerProtocol;

    TigerProxyConfiguration proxyCfg;
}
