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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Map;
import lombok.Data;

@Data
public class CfgDockerOptions {
    /**
     * whether to start container with unmodified entrypoint, or whether to modify by adding pki and other stuff,
     * rewriting the entrypoint
     */
    private boolean proxied = true;
    /**
     * For docker type to trigger OneShotStartupStrategy
     */
    private boolean oneShot = false;
    /**
     * for docker types allows to overwrite the entrypoint cmd configured with in the container
     */
    private String entryPoint;

    /** used only by docker */
    @JsonIgnore
    private Map<Integer, Integer> ports;
}
