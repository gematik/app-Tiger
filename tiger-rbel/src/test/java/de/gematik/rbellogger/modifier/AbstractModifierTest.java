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
package de.gematik.rbellogger.modifier;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractModifierTest {

  public static final RbelKeyFolderInitializer RBEL_KEY_FOLDER_INITIALIZER =
      new RbelKeyFolderInitializer("src/test/resources");
  public static RbelLogger rbelLogger;

  @BeforeEach
  public void initRbelLogger() {
    if (rbelLogger == null) {
      rbelLogger =
          RbelLogger.build(new RbelConfiguration().addInitializer(RBEL_KEY_FOLDER_INITIALIZER));
    }
    rbelLogger.getRbelModifier().deleteAllModifications();
  }

  public RbelElement modifyMessageAndParseResponse(RbelElement message) {
    return rbelLogger.getRbelModifier().applyModifications(message);
  }

  public RbelElement readAndConvertCurlMessage(
      String fileName, Function<String, String>... messageMappers) throws IOException {
    String curlMessage = readCurlFromFileWithCorrectedLineBreaks(fileName);
    for (Function<String, String> mapper : messageMappers) {
      curlMessage = mapper.apply(curlMessage);
    }
    return rbelLogger.getRbelConverter().convertElement(curlMessage, null);
  }
}
