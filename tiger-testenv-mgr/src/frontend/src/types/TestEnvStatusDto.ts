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

import TigerServerStatusUpdateDto from "./TigerServerStatusUpdateDto";
import FeatureUpdate from "./testsuite/FeatureUpdate";
import BannerType from "@/types/BannerType";

export default class TestEnvStatusDto {
  index: number = -1;
  featureMap: Map<string, FeatureUpdate> = new Map<string, FeatureUpdate>();
  servers: Map<string, TigerServerStatusUpdateDto> = new Map<string, TigerServerStatusUpdateDto>();
  bannerMessage:string | null = null;
  bannerColor: string | null = null;
  bannerType: BannerType = BannerType.MESSAGE;
  bannerIsHtml: boolean = false;

  public static sortArray(array:Array<TestEnvStatusDto>) {
    // sort prefetched Messages based on index;
    array.sort((a, b) => {
      return Number(a.index - b.index)
    });
  }

  public static checkMessagesInArrayAreWellOrdered(array:Array<TestEnvStatusDto>): boolean {
    let ctr: number = -1;
    let indexConsistent = true;
    array.every((testEnvStatusDtoMessage) => {
      const index: number = testEnvStatusDtoMessage.index;
      if (ctr === -1) {
        ctr = index;
        return true;
      } else {
        if (ctr + 1 !== index) {
          indexConsistent = false;
          return false;
        }
      }
    });
    return indexConsistent;
  }
}
