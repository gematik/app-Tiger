///
///
/// Copyright 2024 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

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
