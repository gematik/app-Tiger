import ServerType from "./ServerType";
import TigerServerStatus from "./TigerServerStatus";

export default interface TigerServerStatusUpdateDto {
  statusMessage: string;
  type: ServerType;
  baseUrl: string;
  status: TigerServerStatus;
}
