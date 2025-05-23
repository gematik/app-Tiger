///
///
/// Copyright 2021-2025 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import TigerServerStatus from "./TigerServerStatus";

export interface IJsonServerStatusUpdate {
  statusMessage: string;
  type: string;
  baseUrl: string;
  status: string;
}

interface IJsonServerStatusUpdates {
  [key: string]: IJsonServerStatusUpdate;
}

export default class TigerServerStatusUpdateDto {
  statusMessage: string = "";
  type: string = "UNSET";
  baseUrl: string = "";
  status: TigerServerStatus = TigerServerStatus.NEW;

  public static fromJson(
    json: IJsonServerStatusUpdate,
  ): TigerServerStatusUpdateDto {
    const serverStatus = new TigerServerStatusUpdateDto();
    serverStatus.statusMessage = json.statusMessage;
    serverStatus.type = json.type;
    serverStatus.baseUrl = json.baseUrl;
    serverStatus.status = json.status as TigerServerStatus;
    return serverStatus;
  }

  public merge(
    serverStatus: TigerServerStatusUpdateDto,
  ): TigerServerStatusUpdateDto {
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

  public static addToMapFromJson(
    map: Map<string, TigerServerStatusUpdateDto>,
    jsonStatusUpdates: IJsonServerStatusUpdates,
  ) {
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
