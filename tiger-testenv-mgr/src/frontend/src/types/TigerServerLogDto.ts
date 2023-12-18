/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

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
