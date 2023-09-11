/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

export default class TigerConfigurationPropertyDto {
    key: string;
    value: string;
    source: string;


    constructor(key: string, value: string, source: string) {
        this.key = key;
        this.value = value;
        this.source = source;
    }
}