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
package de.gematik.rbellogger.util;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@RequiredArgsConstructor
public class GenericPrettyPrinter<T> {

  private final Predicate<T> isLeaf;
  private final Function<T, String> leafPrinter;
  private final Function<T, Stream<T>> leafRetriever;
  @Setter private Function<T, String> nodeIntroPrinter = t -> "";
  private String openingBrace = "[";
  private String closingBrace = "]";
  private String depthStepper = "\t";

  public String prettyPrint(T root) {
    return prettyPrint(root, 0);
  }

  private String prettyPrint(T node, int depth) {
    if (isLeaf.test(node)) {
      return depthStepper.repeat(depth) + leafPrinter.apply(node);
    } else {
      return depthStepper.repeat(depth)
          + nodeIntroPrinter.apply(node)
          + openingBrace
          + "\n"
          + leafRetriever
              .apply(node)
              .map(leaf -> prettyPrint(leaf, depth + 1))
              .collect(Collectors.joining(",\n"))
          + "\n"
          + depthStepper.repeat(depth)
          + closingBrace;
    }
  }
}
