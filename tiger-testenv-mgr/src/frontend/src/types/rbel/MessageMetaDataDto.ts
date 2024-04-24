/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

export default class MessageMetaDataDto {
  uuid: string = '';
  path: string = '';
  method: string = '';
  menuInfoString: string = '';
  responseCode: number = -1;
  recipient: string = '';
  sender: string = '';
  bundledServerNameSender: string = '';
  bundledServerNameReceiver: string = '';
  sequenceNumber: number = -1;
  timestamp: Date | string = '';
  pairedUuid: string = '';

  public toString() {
    return `{ uuid: "${this.uuid}" }\n`;
  }
}

