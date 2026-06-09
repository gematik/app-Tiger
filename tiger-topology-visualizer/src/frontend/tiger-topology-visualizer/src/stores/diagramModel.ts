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

import { defineStore } from "pinia";
import {
  type ConfigurationDiagramDto,
  createDiagramNodeDto,
} from "../types/topology.ts";
import { ref } from "vue";
import type { ProblemDetails } from "../types/ProblemDetails.ts";

type UploadedFileStatus = "pending" | "processing" | "processed" | "error";

class FetchError extends Error {
  readonly status: number;
  readonly statusText: string;
  readonly body?: ProblemDetails | string | null;

  constructor(
    status: number,
    statusText: string,
    body?: ProblemDetails | string | null,
  ) {
    super(`${status} ${statusText}`);
    this.name = "FetchError";
    this.status = status;
    this.statusText = statusText;
    this.body = body;
  }
}

async function fetchJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { Accept: "application/json", ...init?.headers },
  });

  if (!res.ok) {
    let body: ProblemDetails | string | null;
    const text = await res.text();
    try {
      body = JSON.parse(text) as ProblemDetails;
    } catch {
      body = text || null;
    }
    throw new FetchError(res.status, res.statusText, body);
  }

  return (await res.json()) as T;
}

export const useDiagramModel = defineStore("diagramModel", () => {
  const model = ref<ConfigurationDiagramDto>({
    nodes: [],
    edges: [],
    warnings: [],
  });
  const uploadedFiles = ref<File[]>([]);
  const uploadedFileStatuses = ref<Record<string, UploadedFileStatus>>({});
  const uploadError = ref<string | null>(null);

  function highlightNode(nodeId: string) {
    const node = model.value.nodes.find((node) => node.id === nodeId);
    if (node) {
      node.data.isHighlighted = true;
    }
  }

  function clearHighlight(nodeId: string) {
    const node = model.value.nodes.find((node) => node.id === nodeId);
    if (node) {
      node.data.isHighlighted = false;
    }
  }

  function emptyDiagramModel(): ConfigurationDiagramDto {
    return {
      nodes: [],
      edges: [],
      warnings: [],
    };
  }

  function fileKey(file: File): string {
    return `${file.name}:${file.size}:${file.lastModified}`;
  }

  function getFileStatus(file: File): UploadedFileStatus {
    return uploadedFileStatuses.value[fileKey(file)] ?? "pending";
  }

  function isFileStatus(file: File, status: string): boolean {
    return getFileStatus(file) === status;
  }

  function getUploadError(): string | null {
    return uploadError.value;
  }

  function clearUploadError() {
    uploadError.value = null;
  }

  function updateStatuses(files: File[], status: UploadedFileStatus) {
    uploadedFileStatuses.value = {
      ...uploadedFileStatuses.value,
      ...Object.fromEntries(files.map((file) => [fileKey(file), status])),
    };
  }

  function mergeFiles(currentFiles: File[], additionalFiles: File[]): File[] {
    const seenKeys = new Set(currentFiles.map(fileKey));
    const newFiles = additionalFiles.filter((file) => {
      const key = fileKey(file);
      if (seenKeys.has(key)) {
        return false;
      }
      seenKeys.add(key);
      return true;
    });

    return [...currentFiles, ...newFiles];
  }

  async function refreshDiagramModel() {
    if (!uploadedFiles.value.length) {
      uploadedFileStatuses.value = {};
      uploadError.value = null;
      model.value = emptyDiagramModel();
      return;
    }

    clearUploadError();
    updateStatuses(uploadedFiles.value, "processing");

    const form = new FormData();
    for (const file of uploadedFiles.value) {
      form.append("files", file, file.name);
    }

    try {
      const receivedModel = await fetchJson<ConfigurationDiagramDto>(
        "/topology/upload",
        { method: "POST", body: form },
      );
      model.value = {
        ...receivedModel,
        nodes: receivedModel.nodes.map((item) => createDiagramNodeDto(item)),
      };
      updateStatuses(uploadedFiles.value, "processed");
    } catch (error) {
      uploadError.value = extractUploadErrorMessage(error);
      updateStatuses(uploadedFiles.value, "error");
    }
  }

  async function addConfigurationYaml(files: File | File[]) {
    const list = Array.isArray(files) ? files : [files];
    if (!list.length) {
      return;
    }

    uploadedFiles.value = mergeFiles(uploadedFiles.value, list);
    updateStatuses(list, "pending");
    await refreshDiagramModel();
  }

  async function removeConfigurationYaml(file: File) {
    uploadedFiles.value = uploadedFiles.value.filter(
      (candidate) => fileKey(candidate) !== fileKey(file),
    );
    const remainingStatuses = { ...uploadedFileStatuses.value };
    delete remainingStatuses[fileKey(file)];
    uploadedFileStatuses.value = remainingStatuses;
    await refreshDiagramModel();
  }

  function clearConfigurationYaml() {
    uploadedFiles.value = [];
    uploadedFileStatuses.value = {};
    uploadError.value = null;
    model.value = emptyDiagramModel();
  }

  /**
   * Load topology from a live backend GET endpoint (e.g. in the workflow UI).
   * Replaces the current model with the response data.
   */
  async function loadFromLiveEndpoint(url: string = "/topology") {
    try {
      const receivedModel = await fetchJson<ConfigurationDiagramDto>(url);
      model.value = {
        ...receivedModel,
        nodes: receivedModel.nodes.map((item) => createDiagramNodeDto(item)),
      };
      uploadError.value = null;
    } catch (error) {
      uploadError.value = extractUploadErrorMessage(error);
    }
  }

  return {
    model,
    uploadedFiles,
    uploadError,
    getUploadError,
    getFileStatus,
    isFileStatus,
    clearUploadError,
    addConfigurationYaml,
    removeConfigurationYaml,
    clearConfigurationYaml,
    loadFromLiveEndpoint,
    highlightNode,
    clearHighlight,
  };
});

function extractUploadErrorMessage(error: unknown): string {
  const isNonEmpty = (v: unknown): v is string =>
    typeof v === "string" && v.trim().length > 0;

  if (error instanceof FetchError) {
    const { body } = error;
    if (isNonEmpty(body)) return body;
    if (body && typeof body === "object") {
      if (isNonEmpty(body.detail)) return body.detail;
      if (isNonEmpty(body.title)) return body.title;
    }
    return `${error.status} ${error.statusText}`;
  }

  if (error instanceof Error && isNonEmpty(error.message)) {
    return error.message;
  }

  return "An unexpected error occurred while uploading the topology files.";
}
