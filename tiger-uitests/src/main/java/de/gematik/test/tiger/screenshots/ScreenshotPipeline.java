/*
 * Copyright 2026 gematik GmbH
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
package de.gematik.test.tiger.screenshots;

import java.util.Arrays;

/**
 * Screenshot delta pipeline CLI.
 *
 * <p>Subcommands: {@code publish}, {@code download}, {@code retain}, {@code diff}.
 */
public class ScreenshotPipeline {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: ScreenshotPipeline <publish|download|retain|diff> [options]");
      System.exit(1);
    }
    var remaining = Arrays.copyOfRange(args, 1, args.length);
    var cfg = Config.parse(remaining);

    System.exit(
        switch (args[0]) {
          case "publish" -> new PublishCommand(cfg).run();
          case "download" -> new DownloadCommand(cfg).run();
          case "retain" -> new RetainCommand(cfg).run();
          case "diff" -> new DiffCommand(cfg, remaining).run();
          default -> {
            System.err.println("Unknown command: " + args[0]);
            yield 1;
          }
        });
  }
}
