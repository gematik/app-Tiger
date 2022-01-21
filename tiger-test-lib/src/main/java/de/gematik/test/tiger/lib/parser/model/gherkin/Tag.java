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

package de.gematik.test.tiger.lib.parser.model.gherkin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Tag {

    private String name;
    private String parameter;

    public String getTagString() {
        if (!parameter.isBlank()) {
            return name + ":" + parameter;
        } else {
            return name;
        }
    }

    public static Tag fromString(final String s) {
        final int colon = s.indexOf(":");
        if (colon == -1) {
            return new Tag(s, "");
        } else {
            return new Tag(s.substring(0, colon), s.substring(colon + 1));
        }
    }
}
