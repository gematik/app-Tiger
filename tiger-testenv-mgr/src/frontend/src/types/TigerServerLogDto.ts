import {LocalDateTime} from '@js-joda/core';

export default class TigerServerLogDto {
  serverName: string | null = null;
  logLevel: string | null = null;
  logMessage: string | null = null;
  // first step of mankind on moon ;)
  localDateTime: LocalDateTime = LocalDateTime.of(1969, 7, 21, 2, 56);

  public static fromJson(json: TigerServerLogDto): TigerServerLogDto {
    const receivedLogMessage: TigerServerLogDto = new TigerServerLogDto();
    if (json.logMessage) {
      // remove ansi color before setting
      receivedLogMessage.logMessage = json.logMessage.replace(/\u001b\[.*?m/g, '');
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
