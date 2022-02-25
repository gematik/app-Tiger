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

package de.gematik.test.tiger.common.config;

import lombok.Data;

@Data
public class TigerConfigurationKeyString {
    private final String value;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof String) {
            return value.equalsIgnoreCase((String) o);
        }
        if (!(o instanceof TigerConfigurationKeyString)) return false;

        TigerConfigurationKeyString that = (TigerConfigurationKeyString) o;

        return value != null ? value.equalsIgnoreCase(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        return value != null ? value.toLowerCase().hashCode() : 0;
    }

    public static TigerConfigurationKeyString wrapAsKey(String value){
        return new TigerConfigurationKeyString(value);
    }

    public String asLowerCase() {
        return value.toLowerCase();
    }
}
