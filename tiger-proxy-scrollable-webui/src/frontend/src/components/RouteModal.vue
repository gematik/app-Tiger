<!--


    Copyright 2021-2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    *******

    For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.

-->
<script setup lang="ts">
import { inject, ref } from "vue";
import "simple-syntax-highlighter/dist/sshpre.css";
import { useTigerProxyConfig } from "@/api/TigerRouterConfig.ts";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { toastSymbol } from "@/Toast.ts";
import { faArrowRight, faTrashCan } from "@fortawesome/free-solid-svg-icons";

const toast = inject(toastSymbol)!;
const { isLoading, routes, deleteRoute, addRoute } = useTigerProxyConfig({
  onError: (err) => {
    toast.showToast(err);
  },
});

const fromInput = ref("");
const toInput = ref("");
const fromInputRef = ref<HTMLInputElement | null>(null);
const toInputRef = ref<HTMLInputElement | null>(null);

function validateRouteInput() {
  if (fromInput.value.length === 0 || toInput.value.length === 0) {
    return false;
  }
  try {
    new URL(fromInput.value);
    new URL(toInput.value);
  } catch {
    return false;
  }
  return true;
}

function onAddRoute() {
  if (validateRouteInput()) {
    addRoute(fromInput.value, toInput.value);
    fromInput.value = "";
    toInput.value = "";
  }
}
</script>

<template>
  <div
    class="modal modal-lg fade"
    id="routeModal"
    data-bs-backdrop="static"
    data-bs-keyboard="false"
    tabindex="-1"
    aria-labelledby="routeModalLabel"
    aria-hidden="true"
  >
    <div class="modal-dialog modal-dialog-scrollable">
      <div class="modal-content">
        <div class="modal-header">
          <h1 class="modal-title fs-5 me-3" id="rawContentModalLabel">Route Management</h1>
          <button
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
          ></button>
        </div>
        <div class="modal-body">
          <label class="form-label">Routes:</label>
          <ol class="list-group list-group">
            <li
              v-for="route in routes"
              :key="route.id ?? ''"
              class="list-group-item d-flex justify-content-between align-items-start"
            >
              <div class="d-flex align-items-center gap-2">
                <button
                  class="btn btn-danger me-2"
                  type="button"
                  @click="route.id ? deleteRoute(route.id) : null"
                >
                  <FontAwesomeIcon :icon="faTrashCan" />
                </button>
                <span>{{ route.from }}</span>
                <FontAwesomeIcon :icon="faArrowRight" />
                <span>{{ route.to }}</span>
              </div>
            </li>
          </ol>
          <div class="flex mt-4">
            <label class="form-label">New Route:</label>
            <div class="input-group">
              <span class="input-group-text" id="basic-addon1">from</span>
              <input
                ref="fromInputRef"
                type="text"
                class="form-control"
                placeholder="scheme://host:port"
                v-model="fromInput"
              />
              <span class="input-group-text" id="basic-addon1"
                ><FontAwesomeIcon :icon="faArrowRight" class="me-3" />to</span
              >
              <input
                ref="toInputRef"
                type="text"
                class="form-control"
                placeholder="scheme://host:port"
                v-model="toInput"
              />
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button
            type="button"
            class="btn btn-primary"
            :disabled="fromInput.length === 0 && toInput.length === 0"
            @click="onAddRoute"
          >
            <span
              v-if="isLoading"
              class="spinner-border spinner-border-sm"
              role="status"
              aria-hidden="true"
            />
            Add new Route
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped></style>
