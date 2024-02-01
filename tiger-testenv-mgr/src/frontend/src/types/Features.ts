/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */
export class Features {
  trafficVisualization: boolean = false;


  static fromMap(map: { [x: string]: string; }): Features {
    const features = new Features();
    features.trafficVisualization = map["trafficvisualization"] === "true";
    return features;
  }
}
