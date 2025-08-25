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
package de.gematik.rbellogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.junit.jupiter.api.Test;

class RbelMessageMetadataTest {

  @Test
  void testStringArrayDeserialization() {
    var metadata = new RbelMessageMetadata();
    var metadataEntry = new RbelMessageMetadata.RbelMetadataValue<>("test", String[].class);

    var objectMapper = new ObjectMapper();
    var array = objectMapper.convertValue("[\"foobar\", \"baz\"]", JSONArray.class);

    metadata.addMetadata("test", array);

    assertThat(metadataEntry.getValue(metadata)).isPresent();
    assertThat(metadataEntry.getValue(metadata).get()).hasSize(2);
    assertThat(metadataEntry.getValue(metadata).get()).containsExactly("foobar", "baz");
  }
}
