/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import BannerType from "../types/BannerType";
import BannerMessage from "../types/BannerMessage";

describe("testing BannerMessage class", () => {
  test("fromJson should pass when all steps pass", () => {
    let bannerMessage = BannerMessage.fromJson({"bannerMessage":"BANNER","bannerColor":"Green","bannerType":"MESSAGE","bannerIsHtml":false});
    expect(bannerMessage.text).toBe("BANNER");
    expect(bannerMessage.color).toBe("Green");
    expect(bannerMessage.type).toBe(BannerType.MESSAGE);
    expect(bannerMessage.isHtml).toBe(false);
  });
});