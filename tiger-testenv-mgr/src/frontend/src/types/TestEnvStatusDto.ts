import MessageUpdateDto from "./MessageUpdateDto";
import TigerServerStatusUpdateDto from "./TigerServerStatusUpdateDto";

interface TestEnvStatusDto {
    message: MessageUpdateDto,
    servers: Map<string, TigerServerStatusUpdateDto>
}

export default TestEnvStatusDto;