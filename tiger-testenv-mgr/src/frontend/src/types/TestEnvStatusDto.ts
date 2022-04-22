import DataType from "./DataType";
import TigerServerStatusUpdateDto from "./TigerServerStatusUpdateDto";
import FeatureUpdate from "./FeatureUpdate";

export default interface TestEnvStatusDto {
  featureMap: Map<string, FeatureUpdate>;
  servers: Map<string, TigerServerStatusUpdateDto>;
}
