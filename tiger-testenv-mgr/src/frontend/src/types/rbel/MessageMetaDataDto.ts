/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

export default class MessageMetaDataDto {
  uuid: string = '';
  path: string = '';
  method: string = '';
  responseCode: number = -1;
  recipient: string = '';
  sender: string = '';
  sequenceNumber : number = -1;

  public toString() {
    return `{ uuid: "${this.uuid}" }\n`;
  }
}
