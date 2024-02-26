/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import BannerType from "../types/BannerType";
import BannerMessage from "../types/BannerMessage";

describe("testing BannerMessage class", () => {
  test("fromJson should pass when all steps pass", () => {
    const bannerMessage = BannerMessage.fromJson({
      "bannerMessage": "BANNER",
      "bannerColor": "Green",
      "bannerType": "MESSAGE",
      "bannerIsHtml": false
    });
    expect(bannerMessage.text).toBe("BANNER");
    expect(bannerMessage.color).toBe("Green");
    expect(bannerMessage.type).toBe(BannerType.MESSAGE);
    expect(bannerMessage.isHtml).toBe(false);
  });
});
