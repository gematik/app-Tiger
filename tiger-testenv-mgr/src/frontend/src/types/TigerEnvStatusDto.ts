import TigerServerStatusDto from "./TigerServerStatusDto";

export default interface TigerEnvStatusDto {
  currentStatusMessage: string;
  servers: Map<string, TigerServerStatusDto>;
}