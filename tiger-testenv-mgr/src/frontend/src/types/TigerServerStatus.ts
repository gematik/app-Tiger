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

import TigerServerStatusDto from "@/types/TigerServerStatusDto";

enum TigerServerStatus {
  NEW = "NEW",
  STARTING = "STARTING",
  RUNNING = "RUNNING",
  STOPPED = "STOPPED",
}

export default TigerServerStatus;

function mergeStatus(status1: TigerServerStatus, status2: TigerServerStatus) {
  switch (status1) {
    case TigerServerStatus.STARTING:
    case TigerServerStatus.STOPPED:
      return status1;
    case TigerServerStatus.NEW:
    case TigerServerStatus.RUNNING:
      return status2;
  }
}

export function currentOverallServerStatus(currentServerStatus : Map<string, TigerServerStatusDto> ) : string {
  let status = TigerServerStatus.NEW;
  currentServerStatus.forEach((server) => {
    status = mergeStatus(status, server.status);
  });
  return status.toLowerCase();
}

export function sortedServerList(currentServerStatus : Map<string, TigerServerStatusDto> ) :  Array<TigerServerStatusDto> {
  const arr = Array.from(currentServerStatus.values());
  arr.sort((a,b) => a.type === "local_tiger_proxy" ? -1 : 1);
  return arr;
}

