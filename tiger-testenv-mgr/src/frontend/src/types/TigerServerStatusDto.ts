import ServerType from "./ServerType";
import TigerServerStatus from "./TigerServerStatus";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";

export default class TigerServerStatusDto {
  name: string = "";
  baseUrl: string = "";
  type: ServerType = ServerType.UNSET;
  status: TigerServerStatus = TigerServerStatus.NEW;
  statusMessage: string = "";
  statusUpdates: Array<string> = new Array<string>();

  public static fromUpdateDto(name : string, updateDto : TigerServerStatusUpdateDto) : TigerServerStatusDto {
    const serverStatus:TigerServerStatusDto = new TigerServerStatusDto();
    serverStatus.name = name;
    serverStatus.baseUrl = updateDto.baseUrl;
    if (updateDto) {
      serverStatus.type = updateDto.type;
    }
    if (updateDto.status) {
      serverStatus.status = updateDto.status;
      serverStatus.statusMessage = updateDto.statusMessage;
    }
    return serverStatus
  }
}
