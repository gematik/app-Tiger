/*
 * Copyright (c) 2024 gematik GmbH
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

package io.cucumber.core.plugin.report;

import java.util.Collection;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ClassUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceReportJsonConverter {

  public static String toJson(Object object) {

    if (object == null) {
      return null;
    } else if (object instanceof CharSequence) {
      return object.toString();
    } else if (object instanceof Collection || object.getClass().isArray()) {
      return new JSONArray().putAll(object).toString(2);
    } else if (ClassUtils.isPrimitiveWrapper(object.getClass())) {
      return object.toString();
    } else {
      return new JSONObject(object).toString(2);
    }
  }
}
