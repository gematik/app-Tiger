import TigerServerStatusDto from "./TigerServerStatusDto";

interface TigerEnvStatusDto {
  currentStatusMessage: string;
  servers: Map<string, TigerServerStatusDto>;
}

export default TigerEnvStatusDto;
