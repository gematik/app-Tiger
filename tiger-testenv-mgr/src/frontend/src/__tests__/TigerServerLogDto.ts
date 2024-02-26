/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import TigerServerLogDto from "../types/TigerServerLogDto";
import {LocalDateTime} from "@js-joda/core";

describe("testing TigerServerLogDto class", () => {

  test("empty TigerServerLogDto should contain undefined values", () => {
    const tigerServerLogDto = new TigerServerLogDto();
    expect(tigerServerLogDto.logMessage).toBe(null);
    expect(tigerServerLogDto.serverName).toBe(null);
    expect(tigerServerLogDto.logLevel).toBe(null);
    expect(tigerServerLogDto.localDateTime.toString()).toBe(LocalDateTime.of(1969, 7, 21, 2, 56).toString());
  });

  test("empty JSON applies correctly", () => {
    const tigerServerLogDto = TigerServerLogDto.fromJson(JSON.parse("{ }"));
    expect(tigerServerLogDto.logMessage).toBe(null);
    expect(tigerServerLogDto.serverName).toBe(null);
    expect(tigerServerLogDto.logLevel).toBe(null);
    expect(tigerServerLogDto.localDateTime.toString()).toBe(LocalDateTime.of(1969, 7, 21, 2, 56).toString());
  });

  test("JSON applies correctly", () => {
    const tigerServerLogDto = TigerServerLogDto.fromJson(JSON.parse('{ "serverName":"winstone","logLevel":"INFO","localDateTime":"2022-06-14T08:17:47.1737913","logMessage":"Loading PKI resources for instance winstone..." }'));
    expect(tigerServerLogDto.logMessage).toBe("Loading PKI resources for instance winstone...");
    expect(tigerServerLogDto.serverName).toBe("winstone");
    expect(tigerServerLogDto.logLevel).toBe("INFO");
    expect(tigerServerLogDto.localDateTime.toString()).toBe(LocalDateTime.of(2022, 6, 14, 8, 17, 47, 173791300 ).toString());
  });
});
