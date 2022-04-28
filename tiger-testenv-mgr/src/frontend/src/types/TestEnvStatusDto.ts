import DataType from "./DataType";
import TigerServerStatusUpdateDto from "./TigerServerStatusUpdateDto";
import FeatureUpdate from "./testsuite/FeatureUpdate";
import BannerMessages from "@/types/BannerMessages";

export default interface TestEnvStatusDto {
  index: number;
  featureMap: Map<string, FeatureUpdate>;
  servers: Map<string, TigerServerStatusUpdateDto>;
}
