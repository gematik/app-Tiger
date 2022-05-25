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
