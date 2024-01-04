/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
export class ExperimentalFeatures {
  trafficVisualization: boolean = false;


  static fromMap(map: { [x: string]: string; }): ExperimentalFeatures {
    const features = new ExperimentalFeatures();
    features.trafficVisualization = map["trafficvisualization"] === "true";
    return features;
  }
}
