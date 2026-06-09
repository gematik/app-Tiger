///
///
/// Copyright 2021-2026 gematik GmbH
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
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

import type { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { fab } from "@fortawesome/free-brands-svg-icons";
import { fas } from "@fortawesome/free-solid-svg-icons";

export type NodeVisual = {
  label: string;
  icon?: IconDefinition;
  colors: {
    background: string;
    border: string;
    text: string;
    borderStyle?: "solid" | "dashed";
  };
};

const defaultColors = {
  background: "#dbeafe",
  border: "#1e3a8a",
  text: "#1e3a8a",
};

export const NODE_VISUALS: Record<string, NodeVisual> = {
  default: {
    label: "Server",
    icon: fas.faServer,
    colors: defaultColors,
  },
  group: {
    label: "Group",
    colors: {
      background: "transparent",
      border: "#1e3a8a",
      text: "#1e3a8a",
      borderStyle: "dashed",
    },
  },
  tigerProxy: {
    label: "Tiger Proxy",
    icon: fas.faProjectDiagram,
    colors: {
      background: "#ffa55c",
      border: "#fb923c",
      text: "#1f2937",
    },
  },
  tigerProxyExternal: {
    label: "Tiger Proxy (External)",
    icon: fas.faProjectDiagram,
    colors: {
      background: "rgba(255, 165, 92, 0.6)",
      border: "rgba(251, 146, 60, 0.6)",
      text: "#1f2937",
      borderStyle: "dashed",
    },
  },
  externalJar: {
    label: "External Jar",
    icon: fas.faRocket,
    colors: defaultColors,
  },
  externalUrl: {
    label: "External URL",
    icon: fas.faExternalLinkAlt,
    colors: defaultColors,
  },
  docker: {
    label: "Docker",
    icon: fab.faDocker,
    colors: defaultColors,
  },
  composeService: {
    label: "Compose Service",
    icon: fas.faCubes,
    colors: defaultColors,
  },
  zion: {
    label: "Zion",
    icon: fas.faZ,
    colors: defaultColors,
  },
  httpbin: {
    label: "Httpbin",
    icon: fas.faTrash,
    colors: defaultColors,
  },
  route: {
    label: "Route",
    icon: fas.faCircleNodes,
    colors: {
      background: "#fed7aa",
      border: "#fed7aa",
      text: "#7c2d12",
    },
  },
};

export function getNodeVisual(type?: string): NodeVisual {
  return NODE_VISUALS[type ?? "default"] ?? NODE_VISUALS.default;
}
