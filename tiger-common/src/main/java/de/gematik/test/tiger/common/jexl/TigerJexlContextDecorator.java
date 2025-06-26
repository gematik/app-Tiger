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
package de.gematik.test.tiger.common.jexl;

import java.util.Optional;

/**
 * Interface which allows the decoration (adding more items to) a JEXL-Context that is being
 * created.
 */
public interface TigerJexlContextDecorator {

  /**
   * Decorate the given context using the information provided. This is highly contextual since this
   * can be called with different kinds of elements (hence only Object)
   *
   * @param element The current element (CAN be of type RbelElement. To avoid circular dependencies
   *     it is taken as Object)
   * @param key The key, if given, of the current element
   * @param context The context that is being build
   */
  void decorate(Object element, Optional<String> key, TigerJexlContext context);
}
