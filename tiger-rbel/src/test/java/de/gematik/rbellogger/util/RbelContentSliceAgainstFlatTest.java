/*
 *
 * Copyright 2021-2026 gematik GmbH
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class RbelContentSliceAgainstFlatTest {

  private static final byte[] TEXT = "  hello, brave\r\nnew world  \t hello, brave".getBytes(UTF_8);

  private static Object answer(Function<RbelContent, Object> operation, RbelContent content) {
    try {
      Object result = operation.apply(content);
      if (result instanceof List<?> list) {
        return list.stream()
            .map(item -> item instanceof RbelContent c ? c.toReadableString() : item)
            .toList();
      }
      return result;
    } catch (RuntimeException e) {
      return "throws " + e.getClass().getSimpleName();
    }
  }

  private static List<byte[]> probes() {
    var probes = new ArrayList<byte[]>();
    probes.add(new byte[0]);
    for (int from = 0; from < TEXT.length; from++) {
      for (int length = 1; length <= 6 && from + length <= TEXT.length; length++) {
        probes.add(Arrays.copyOfRange(TEXT, from, from + length));
      }
    }
    return probes;
  }

  private static Map<String, String> disagreements() {
    var base = RbelContent.of(TEXT.clone());
    var found = new TreeMap<String, String>();
    for (int from = 0; from <= TEXT.length; from++) {
      for (int to = from; to <= TEXT.length; to++) {
        var slice = base.subArray(from, to);
        var flat = RbelContent.of(Arrays.copyOfRange(TEXT, from, to));
        int size = to - from;
        String where = "[" + from + "," + to + ")";

        compare(found, "toByteArray", where, c -> Arrays.toString(c.toByteArray()), slice, flat);
        for (byte[] probe : probes()) {
          String text =
              where
                  + " with '"
                  + new String(probe, UTF_8).replace("\r", "\\r").replace("\n", "\\n")
                  + "'";
          compare(found, "startsWith(p)", text, c -> c.startsWith(probe), slice, flat);
          compare(found, "endsWith(p)", text, c -> c.endsWith(probe), slice, flat);
          compare(
              found, "startsTrimmedWith(p)", text, c -> c.startsTrimmedWith(probe), slice, flat);
          compare(found, "endsTrimmedWith(p)", text, c -> c.endsTrimmedWith(probe), slice, flat);
          compare(
              found,
              "startsWithIgnoreCase(p)",
              text,
              c -> c.startsWithIgnoreCase(probe, UTF_8),
              slice,
              flat);
          compare(
              found,
              "startsTrimmedWithIgnoreCase(p)",
              text,
              c -> c.startsTrimmedWithIgnoreCase(probe, UTF_8),
              slice,
              flat);
          compare(
              found,
              "endsTrimmedWithIgnoreCase(p)",
              text,
              c -> c.endsTrimmedWithIgnoreCase(probe, UTF_8),
              slice,
              flat);
          compare(found, "contains(p)", text, c -> c.contains(probe), slice, flat);
          compare(found, "indexOf(p)", text, c -> c.indexOf(probe), slice, flat);
          for (int start = 0; start <= size; start++) {
            int at = start;
            String atText = text + " from " + at;
            compare(found, "indexOf(p, from)", atText, c -> c.indexOf(probe, at), slice, flat);
            compare(
                found, "startsWith(p, from)", atText, c -> c.startsWith(probe, at), slice, flat);
          }
          if (probe.length == 1) {
            compare(found, "indexOf(b)", text, c -> c.indexOf(probe[0]), slice, flat);
            compare(
                found,
                "countOccurrencesUpTo(b, 3)",
                text,
                c -> c.countOccurrencesUpTo(probe[0], 3),
                slice,
                flat);
            for (int start = 0; start <= size; start++) {
              int at = start;
              compare(
                  found,
                  "indexOf(b, from)",
                  text + " from " + at,
                  c -> c.indexOf(probe[0], at),
                  slice,
                  flat);
            }
          }
        }
        for (String delimiter : List.of("\r\n", " ", "l")) {
          compare(
              found,
              "split(d)",
              where + " by '" + delimiter.replace("\r\n", "\\r\\n") + "'",
              c -> c.split(delimiter.getBytes(UTF_8)),
              slice,
              flat);
        }
      }
    }
    return found;
  }

  private static void compare(
      Map<String, String> found,
      String operation,
      String where,
      Function<RbelContent, Object> run,
      RbelContent slice,
      RbelContent flat) {
    Object sliceAnswer = answer(run, slice);
    Object flatAnswer = answer(run, flat);
    if (!Objects.equals(sliceAnswer, flatAnswer)) {
      found.putIfAbsent(
          operation,
          where + ": the slice says " + sliceAnswer + ", the flat content " + flatAnswer);
    }
  }

  @Test
  void aSliceAnswersEveryQuestionAsAFlatContentOfTheSameBytesDoes() {
    var disagreements = disagreements();

    assertThat(disagreements)
        .as("operations in which a slice differs from a flat content, with the first example")
        .isEmpty();
  }
}
