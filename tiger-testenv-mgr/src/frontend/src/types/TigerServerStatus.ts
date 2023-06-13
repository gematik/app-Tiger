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

