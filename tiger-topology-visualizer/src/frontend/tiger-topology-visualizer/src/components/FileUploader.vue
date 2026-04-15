<!--
  - Copyright 2021-2026 gematik GmbH
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -
  - *******
  -
  - For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
  -
  -->

<script lang="ts" setup>
import FileUpload, { type FileUploadUploaderEvent } from "primevue/fileupload";
import { ref } from "vue";
import { useDiagramModel } from "../stores/diagramModel.ts";
import Message from "primevue/message";

const diagramModel = useDiagramModel();
const isFileListExpanded = ref(true);

function getUploadError(): string | null {
  const store = diagramModel as typeof diagramModel & {
    uploadError?: string | null;
    getUploadError?: () => string | null;
  };

  return store.getUploadError?.() ?? store.uploadError ?? null;
}

function isFileStatus(file: File, status: string): boolean {
  const store = diagramModel as typeof diagramModel & {
    isFileStatus?: (candidate: File, expectedStatus: string) => boolean;
    getFileStatus: (candidate: File) => string;
  };

  return (
    store.isFileStatus?.(file, status) ?? store.getFileStatus(file) === status
  );
}

function onUploadError(event: any) {
  console.error("File upload error:", event);
}

async function uploader(event: FileUploadUploaderEvent) {
  isFileListExpanded.value = true;
  await diagramModel.addConfigurationYaml(event.files);
}

async function removeFile(
  index: number,
  file: File,
  removeFileCallback: (index: number) => void,
) {
  removeFileCallback(index);
  await diagramModel.removeConfigurationYaml(file);
}

function onClear() {
  isFileListExpanded.value = true;
  diagramModel.clearConfigurationYaml();
}

function toggleFileList() {
  isFileListExpanded.value = !isFileListExpanded.value;
}

function formatFileSize(fileSize: number): string {
  if (fileSize < 1024) {
    return `${fileSize} B`;
  }

  const units = ["KB", "MB", "GB"];
  let size = fileSize / 1024;
  let unitIndex = 0;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 10 || unitIndex > 0 ? 0 : 1)} ${units[unitIndex]}`;
}

function fileStatusLabel(file: File): string {
  const status = diagramModel.getFileStatus(file) as string;

  switch (status) {
    case "processing":
      return "Processing";
    case "processed":
      return "Processed";
    case "error":
      return "Error";
    default:
      return "Pending";
  }
}
</script>

<template>
  <FileUpload
    mode="advanced"
    name="files"
    :auto="true"
    :multiple="true"
    :max-file-size="1000000"
    :custom-upload="true"
    :show-upload-button="false"
    cancel-label="Clear files"
    @uploader="uploader"
    @clear="onClear"
    @error="onUploadError"
    accept=".yaml,.yml"
  >
    <template #content="{ files, removeFileCallback }">
      <div v-if="files.length" class="uploaded-files">
        <Message v-if="getUploadError()" severity="error">
          {{ getUploadError() }}
        </Message>
        <button
          type="button"
          class="uploaded-files__toggle"
          :aria-expanded="isFileListExpanded"
          @click="toggleFileList"
        >
          <span class="uploaded-files__toggle-text">
            {{ isFileListExpanded ? "Hide files" : "Show files" }} ({{
              files.length
            }})
          </span>
          <span class="uploaded-files__toggle-icon">{{
            isFileListExpanded ? "▾" : "▸"
          }}</span>
        </button>

        <div v-if="isFileListExpanded" class="uploaded-files__list">
          <div
            v-for="(file, index) in files"
            :key="`${file.name}-${file.size}-${file.lastModified}`"
            class="uploaded-file"
          >
            <div class="uploaded-file__details">
              <div class="uploaded-file__name">{{ file.name }}</div>
              <div class="uploaded-file__meta">
                {{ formatFileSize(file.size) }}
              </div>
            </div>
            <div class="uploaded-file__actions">
              <span
                class="uploaded-file__status"
                :class="{
                  'uploaded-file__status--pending': isFileStatus(
                    file,
                    'pending',
                  ),
                  'uploaded-file__status--processing': isFileStatus(
                    file,
                    'processing',
                  ),
                  'uploaded-file__status--processed': isFileStatus(
                    file,
                    'processed',
                  ),
                  'uploaded-file__status--error': isFileStatus(file, 'error'),
                }"
              >
                {{ fileStatusLabel(file) }}
              </span>
              <button
                type="button"
                class="uploaded-file__remove"
                aria-label="Remove file"
                @click="removeFile(index, file, removeFileCallback)"
              >
                ×
              </button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="uploaded-files__empty">
        Choose one or more YAML files to generate a topology diagram.
      </div>
    </template>
  </FileUpload>
</template>
<style scoped>
.uploaded-files {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
}

.uploaded-files__toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.6rem 0.9rem;
  border: 1px solid #d1d5db;
  border-radius: 0.75rem;
  background: #f9fafb;
  cursor: pointer;
  font-weight: 600;
}

.uploaded-files__toggle:hover {
  background: #f3f4f6;
}

.uploaded-files__toggle-icon {
  font-size: 1rem;
}

.uploaded-files__list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.uploaded-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.75rem;
  background: #fff;
}

.uploaded-file__details {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.uploaded-file__name {
  font-weight: 600;
}

.uploaded-file__meta,
.uploaded-files__empty {
  color: #6b7280;
}

.uploaded-file__actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.uploaded-file__status {
  padding: 0.25rem 0.65rem;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 600;
}

.uploaded-file__status--pending,
.uploaded-file__status--processing {
  background: #ffedd5;
  color: #c2410c;
}

.uploaded-file__status--processed {
  background: #dcfce7;
  color: #166534;
}

.uploaded-file__status--error {
  background: #fee2e2;
  color: #b91c1c;
}

.uploaded-file__remove {
  border: none;
  background: transparent;
  color: #ef4444;
  font-size: 1.5rem;
  line-height: 1;
  cursor: pointer;
}

.uploaded-file__remove:hover {
  color: #b91c1c;
}
</style>
