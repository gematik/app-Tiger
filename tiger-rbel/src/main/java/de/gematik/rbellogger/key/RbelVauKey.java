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

package de.gematik.rbellogger.key;

import lombok.Getter;

import java.security.Key;
import java.util.List;

@Getter
public class RbelVauKey extends RbelKey {

    private final RbelKey parentKey;

    public RbelVauKey(Key key, String keyName, int precedence, RbelKey parentKey) {
        super(key, keyName, precedence);
        this.parentKey = parentKey;
    }
}
