<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">
</script>

<template>
  <div>
    <button type="button" title="edit property"
            class="btn btn-sm btn-outline-primary me-1" data-action="edit"
    >
      <i class="fa fa-solid fa-pencil" id="test-tg-config-editor-btn-edit" data-action="edit"/>
    </button>
    <button type="button" title="delete property from current test execution configuration"
            class="btn btn-sm btn-outline-danger" data-action="delete">
      <i class="fa fa-solid fa-trash" id="test-tg-config-editor-btn-delete" data-action="delete"/>
    </button>
  </div>
</template>
