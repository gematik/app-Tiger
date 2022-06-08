/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

import BannerType from "../types/BannerType";
import BannerMessage from "../types/BannerMessage";

describe("testing BannerMessage class", () => {
  const banner:string = '{"bannerMessage":"BANNER","bannerColor":"Green","bannerType":"MESSAGE"}';

  test("fromJson should pass when all steps pass", () => {
    let bannerMessage = BannerMessage.fromJson({"bannerMessage":"BANNER","bannerColor":"Green","bannerType":"MESSAGE"});
    expect(bannerMessage.text).toBe("BANNER");
    expect(bannerMessage.color).toBe("Green");
    expect(bannerMessage.type).toBe(BannerType.MESSAGE);
  });
});
