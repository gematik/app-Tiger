import TigerServerStatus from "./TigerServerStatus";

export interface IJsonServerStatusUpdate {
  statusMessage: string;
  type: string;
  baseUrl: string;
  status: string;
}

interface IJsonServerStatusUpdates {
  [key: string]: IJsonServerStatusUpdate
}

export default class TigerServerStatusUpdateDto {
  statusMessage: string = '';
  type: string = "UNSET";
  baseUrl: string = '';
  status: TigerServerStatus = TigerServerStatus.NEW;


  public static fromJson(json: IJsonServerStatusUpdate): TigerServerStatusUpdateDto {
    const serverStatus = new TigerServerStatusUpdateDto();
    serverStatus.statusMessage = json.statusMessage;
    serverStatus.type = json.type;
    serverStatus.baseUrl = json.baseUrl;
    serverStatus.status = json.status as TigerServerStatus;
    return serverStatus;
  }


  public merge(serverStatus: TigerServerStatusUpdateDto) : TigerServerStatusUpdateDto {
    if (serverStatus.statusMessage) {
      this.statusMessage = serverStatus.statusMessage;
    }
    if (serverStatus.type) {
      this.type = serverStatus.type;
    }
    if (serverStatus.baseUrl) {
      this.baseUrl = serverStatus.baseUrl;
    }
    if (serverStatus.status) {
      this.status = serverStatus.status;
    }
    return this;
  }

  public static addToMapFromJson(map: Map<string, TigerServerStatusUpdateDto>, jsonStatusUpdates: IJsonServerStatusUpdates) {
    if (jsonStatusUpdates) {
      Object.entries(jsonStatusUpdates).forEach(([key, value]) => {
        if (map.has(key)) {
          map.get(key)?.merge(this.fromJson(value));
        } else {
          map.set(key, this.fromJson(value));
        }
      });
    }
  }

}
