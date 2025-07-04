/*
 * Copyright 2025 gematik GmbH
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
 * ******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 *
 */

package de.gematik.test.tiger.mockserver.logging;

import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;

@AllArgsConstructor
public class ChannelContextLogger {
  private final Logger log;

  public void logStage(ChannelHandlerContext ctx, String message) {
    log.atTrace()
        .log(
            () ->
                message
                    + " for channel: %s pipeline: %s"
                        .formatted(ctx.channel().toString(), ctx.pipeline().names()));
  }
}
