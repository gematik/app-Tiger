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
package de.gematik.test.tiger.mockserver.model;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/*
 * @author jamesdbloom
 */
public abstract class ObjectWithReflectiveEqualsHashCodeToString {

  private static final String[] IGNORE_KEY_FIELD = {};

  protected String[] fieldsExcludedFromEqualsAndHashCode() {
    return IGNORE_KEY_FIELD;
  }

  @Override
  public String toString() {
    return new ReflectionToStringBuilder(
            this,
            ToStringStyle.SHORT_PREFIX_STYLE,
            null,
            ObjectWithReflectiveEqualsHashCodeToString.class,
            false,
            false)
        .setExcludeFieldNames(fieldsExcludedFromEqualsAndHashCode())
        .toString();
  }

  @Override
  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    return new EqualsBuilder()
        .setExcludeFields(fieldsExcludedFromEqualsAndHashCode())
        .setReflectUpToClass(ObjectWithReflectiveEqualsHashCodeToString.class)
        .setTestTransients(false)
        .setTestRecursive(false)
        .reflectionAppend(this, other)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this, fieldsExcludedFromEqualsAndHashCode());
  }
}
