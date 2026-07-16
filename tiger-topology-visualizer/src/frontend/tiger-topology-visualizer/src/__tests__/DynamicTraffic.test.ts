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

import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, vi, describe, it, expect } from "vitest";
import { defineComponent } from "vue";

vi.mock("@highlightjs/vue-plugin", () => ({
  default: {
    component: defineComponent({
      name: "HighlightJsStub",
      props: {
        autodetect: Boolean,
        language: String,
        code: String,
      },
      setup: () => () => null,
    }),
  },
}));

import { useDiagramModel, type AdditionalEdge } from "../index";

function createAdditionalEdge(
  overrides: Partial<AdditionalEdge> = {},
): AdditionalEdge {
  return {
    sender: "sender-node",
    receiver: "target-node",
    proxiedVia: "my-proxy",
    ...overrides,
  };
}

describe("dynamicTraffic edges", () => {
  const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);

  beforeEach(() => {
    setActivePinia(createPinia());
    warnSpy.mockClear();
  });

  afterEach(() => {
    warnSpy.mockClear();
  });

  it("keeps the edge connected to the expected target node", () => {
    const store = useDiagramModel();

    store.addAdditionalEdge(createAdditionalEdge());

    expect(store.model.edges).toHaveLength(1);
    expect(store.model.edges[0]).toEqual(
      expect.objectContaining({
        id: "edge-sender-node-target-node",
        source: "sender-node",
        target: "target-node",
        type: "dynamicTraffic",
        data: { proxiedVia: "my-proxy" },
      }),
    );
    expect(store.model.nodes.map((node) => node.id)).toEqual([
      "sender-node",
      "target-node",
    ]);
  });

  it("collapses reverse dynamicTraffic edges and keeps a valid connection to the target", () => {
    const store = useDiagramModel();

    store.addAdditionalEdge(
      createAdditionalEdge({ sender: "proxy-a", receiver: "proxy-b" }),
    );
    store.addAdditionalEdge(
      createAdditionalEdge({ sender: "proxy-b", receiver: "proxy-a" }),
    );

    expect(store.model.edges).toHaveLength(1);
    expect(store.model.edges[0]).toEqual(
      expect.objectContaining({
        id: "edge-proxy-a-proxy-b",
        source: "proxy-a",
        target: "proxy-b",
        markerEnd: "arrowclosed",
        markerStart: "arrowclosed",
      }),
    );
  });

  it("reuses an existing target node instead of creating a duplicate one", () => {
    const store = useDiagramModel();

    store.enrichModel({
      nodes: [
        {
          id: "known-target",
          type: "tigerProxy",
          data: { label: "known-target" },
          parentNode: undefined,
          expandParent: undefined,
          position: { x: 0, y: 0 },
        },
      ],
      edges: [],
      warnings: [],
    });

    store.addAdditionalEdge(
      createAdditionalEdge({
        sender: "new-sender",
        receiver: "known-target",
      }),
    );

    expect(store.model.nodes.map((node) => node.id)).toEqual([
      "known-target",
      "new-sender",
    ]);
    expect(store.model.edges).toContainEqual(
      expect.objectContaining({
        source: "new-sender",
        target: "known-target",
      }),
    );
  });

  it("adds a list of additional edges in one call and keeps all expected target connections", () => {
    const store = useDiagramModel();

    store.addAdditionalEdges([
      createAdditionalEdge({ sender: "client-a", receiver: "service-a" }),
      createAdditionalEdge({ sender: "client-b", receiver: "service-b" }),
    ]);

    expect(store.model.edges).toHaveLength(2);
    expect(store.model.edges).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: "edge-client-a-service-a",
          source: "client-a",
          target: "service-a",
        }),
        expect.objectContaining({
          id: "edge-client-b-service-b",
          source: "client-b",
          target: "service-b",
        }),
      ]),
    );
  });

  it("deduplicates same-direction and reverse-direction duplicates within one batch", () => {
    const store = useDiagramModel();

    store.addAdditionalEdges([
      createAdditionalEdge({ sender: "proxy-a", receiver: "proxy-b" }),
      createAdditionalEdge({ sender: "proxy-a", receiver: "proxy-b" }),
      createAdditionalEdge({ sender: "proxy-b", receiver: "proxy-a" }),
    ]);

    expect(store.model.edges).toHaveLength(1);
    expect(store.model.edges[0]).toEqual(
      expect.objectContaining({
        id: "edge-proxy-a-proxy-b",
        source: "proxy-a",
        target: "proxy-b",
      }),
    );
  });

  it("logs when an additional edge is excluded because sender or receiver is blank", () => {
    const store = useDiagramModel();

    store.addAdditionalEdge(
      createAdditionalEdge({ sender: "   ", receiver: "target-node" }),
    );

    expect(store.model.edges).toHaveLength(0);
    expect(warnSpy).toHaveBeenCalledWith(
      "Additional edge excluded: sender or receiver is blank",
      expect.objectContaining({
        sender: "   ",
        receiver: "target-node",
      }),
    );
  });

  it("logs when an additional edge is excluded because the edge id already exists", () => {
    const store = useDiagramModel();

    store.addAdditionalEdge(createAdditionalEdge());
    store.addAdditionalEdge(createAdditionalEdge());

    expect(store.model.edges).toHaveLength(1);
  });

  it("avoids duplicates across repeated batch calls", () => {
    const store = useDiagramModel();

    store.addAdditionalEdges([
      createAdditionalEdge({ sender: "sender-a", receiver: "receiver-a" }),
    ]);
    store.addAdditionalEdges([
      createAdditionalEdge({ sender: "sender-a", receiver: "receiver-a" }),
    ]);

    expect(store.model.edges).toHaveLength(1);
  });

  it("collapses reverse dynamicTraffic edges without adding duplicates", () => {
    const store = useDiagramModel();

    store.addAdditionalEdge(
      createAdditionalEdge({ sender: "proxy-a", receiver: "proxy-b" }),
    );
    store.addAdditionalEdge(
      createAdditionalEdge({ sender: "proxy-b", receiver: "proxy-a" }),
    );

    expect(store.model.edges).toHaveLength(1);
    expect(store.model.edges[0]).toEqual(
      expect.objectContaining({
        id: "edge-proxy-a-proxy-b",
        source: "proxy-a",
        target: "proxy-b",
        markerEnd: "arrowclosed",
        markerStart: "arrowclosed",
      }),
    );
  });
});
