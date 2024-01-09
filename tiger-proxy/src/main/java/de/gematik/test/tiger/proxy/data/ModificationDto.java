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

package de.gematik.test.tiger.proxy.data;

import de.gematik.test.tiger.common.config.RbelModificationDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Data
public class ModificationDto {

  private String name;
  private String condition;
  private String targetElement;
  private String replaceWith;
  private String regexFilter;

  public static ModificationDto from(RbelModificationDescription modification) {
    return ModificationDto.builder()
        .name(modification.getName())
        .condition(modification.getCondition())
        .targetElement(modification.getTargetElement())
        .replaceWith(modification.getReplaceWith())
        .regexFilter(modification.getRegexFilter())
        .build();
  }
}
