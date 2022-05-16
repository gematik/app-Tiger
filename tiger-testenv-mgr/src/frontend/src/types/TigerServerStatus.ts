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

