import TigerServerStatusDto from "./TigerServerStatusDto";

export default interface TigerEnvStatusDto {
  currentIndex: number;
  currentStatusMessage: string;
  servers: Map<string, TigerServerStatusDto>;
}
