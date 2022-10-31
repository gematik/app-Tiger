import TigerServerStatus from "./TigerServerStatus";
import TigerServerStatusUpdateDto from "@/types/TigerServerStatusUpdateDto";

export interface IJsonServerStatus {
  name: string;
  baseUrl: string;
  type: string;
  status: string;
  statusMessage: string;
  statusUpdates: Array<string>;
}

interface IJsonServerStatuus {
  [key: string]: IJsonServerStatus
}

export default class TigerServerStatusDto {
  name: string = "";
  baseUrl: string = "";
  type: string = "UNSET";
  status: TigerServerStatus = TigerServerStatus.NEW;
  statusMessage: string = "";
  statusUpdates: Array<string> = new Array<string>();

  public static fromJson(json : IJsonServerStatus) : TigerServerStatusDto {
    const status  = new TigerServerStatusDto();
    if (json.name) {
      status.name = json.name;
    }
    if (json.baseUrl) {
      status.baseUrl = json.baseUrl;
    }
    if (json.type) {
      status.type = json.type;
    }
    if (json.status) {
      status.status = json.status as TigerServerStatus;
    }
    if (json.statusMessage) {
      status.statusMessage = json.statusMessage;
    }
    if (json.statusUpdates) {
      status.statusUpdates = json.statusUpdates;
    }
    return status;
  }

  public merge(newStatus : TigerServerStatusDto) {
    if (newStatus) {
      // update
      if (newStatus.type) {
        this.type = newStatus.type;
      }
      if (newStatus.baseUrl) {
        this.baseUrl = newStatus.baseUrl;
      }
      if (newStatus.status) {
        this.status = newStatus.status;
      }
      if (newStatus.statusMessage) {
        this.statusMessage = newStatus.statusMessage;
        this.statusUpdates = new Array<string>(newStatus.statusMessage);
      }
    }
  }

  public mergeFromUpdateDto(newStatus: TigerServerStatusUpdateDto) {
    if (newStatus) {
      if (newStatus.type) {
        this.type = newStatus.type;
      }
      if (newStatus.baseUrl) {
        this.baseUrl = newStatus.baseUrl;
      }
      if (newStatus.status) {
        this.status = newStatus.status;
      }
      if (newStatus.statusMessage) {
        this.statusMessage = newStatus.statusMessage;
        this.statusUpdates.push(newStatus.statusMessage);
      }
    }
  }

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

  public static addToMapFromJson(map: Map<string, TigerServerStatusDto>, jsonStatuus: IJsonServerStatuus) {
    if (jsonStatuus) {
      Object.entries(jsonStatuus).forEach(([key, value]) => {
        if (map.has(key)) {
          map.get(key)?.merge(this.fromJson(value));
        } else {
          map.set(key, this.fromJson(value));
        }
      });
    }
  }

}
