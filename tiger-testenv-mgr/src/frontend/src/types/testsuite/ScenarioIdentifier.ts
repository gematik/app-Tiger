/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */


export default class ScenarioIdentifier {
  scenarioUri: string;
  location: { line: number; column: number };
  variantIndex: number;

  constructor(scenarioUri: string,
              location: { line: number; column: number },
              variantIndex: number = -1
  ) {
    this.scenarioUri = scenarioUri;
    this.location = location;
    this.variantIndex = variantIndex;
  }
}