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
<script setup lang="ts"></script>

<template>
  <div>
    <button
      type="button"
      title="edit property"
      class="btn btn-sm btn-outline-primary me-1"
      data-action="edit"
    >
      <i
        id="test-tg-config-editor-btn-edit"
        class="fa fa-solid fa-pencil"
        data-action="edit"
      />
    </button>
    <button
      type="button"
      title="delete property from current test execution configuration"
      class="btn btn-sm btn-outline-danger"
      data-action="delete"
    >
      <i
        id="test-tg-config-editor-btn-delete"
        class="fa fa-solid fa-trash"
        data-action="delete"
      />
    </button>
  </div>
</template>
