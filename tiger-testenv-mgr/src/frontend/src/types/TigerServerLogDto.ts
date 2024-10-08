///
///
/// Copyright 2024 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {LocalDateTime} from '@js-joda/core';

export default class TigerServerLogDto {
    serverName: string | null = null;
    logLevel: string | null = null;
    logMessage: string | null = null;

    localDateTime: LocalDateTime = LocalDateTime.of(1969, 7, 21, 2, 56);

    public static fromJson(json: TigerServerLogDto): TigerServerLogDto {
        const receivedLogMessage: TigerServerLogDto = new TigerServerLogDto();
        if (json.logMessage) {
            // remove ansi color control codes
            receivedLogMessage.logMessage = json.logMessage.replace(/\u001b\[.*?m/g, ''); // NOSONAR
        }
        if (json.serverName) {
            receivedLogMessage.serverName = json.serverName;
        }
        if (json.localDateTime) {
            receivedLogMessage.localDateTime = LocalDateTime.parse(json.localDateTime.toString());
        }
        if (json.logLevel) {
            receivedLogMessage.logLevel = json.logLevel;
        }
        return receivedLogMessage;
    }
}
