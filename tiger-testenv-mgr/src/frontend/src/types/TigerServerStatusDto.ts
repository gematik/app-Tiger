import ServerType from "./ServerType";
import TigerServerStatus from "./TigerServerStatus";

interface TigerServerStatusDto {
  name: string;
  baseUrl: string;
  type: ServerType;
  status: TigerServerStatus;
  statusMessage: string;
  statusUpdates: Array<string>;
}

export default TigerServerStatusDto;
