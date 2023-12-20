<!--
  - ${GEMATIK_COPYRIGHT_STATEMENT}
  -->

<script setup lang="ts">

const props = defineProps<{
  dialogIsOpen: boolean,
  header: string,
  description: string,
  labelConfirmButton: string,
  labelDismissButton: string,
}>()

defineEmits(['click-confirm', 'click-dismiss'])

</script>

<template>
  <transition>
    <teleport to="body">
      <div v-if="dialogIsOpen" class="modal-backdrop fade show"></div>
    </teleport>
  </transition>
  <teleport to="body">
    <transition>
      <div v-if="dialogIsOpen" class="modal modal-sheet d-block bg-body-secondary p-4 py-md-5" tabindex="-1"
           role="dialog"
           id="modal-confirm">
        <div class="modal-dialog" role="document">
          <div class="modal-content rounded-3 shadow">
            <div class="modal-body p-4 text-center">
              <h5 class="mb-0" style="color: var(--gem-primary-400)">{{ props.header }}</h5>
              <p class="mb-0">{{ props.description }}</p>
            </div>
            <div class="modal-footer flex-nowrap p-0">
              <button type="button"
                      @click="$emit('click-confirm')"
                      class="btn btn-lg btn-link fs-6 text-decoration-none col-6 py-3 m-0 rounded-0 border-end">
                <strong>{{
                    props.labelConfirmButton
                  }}</strong></button>
              <button type="button" class="btn btn-lg btn-link fs-6 text-decoration-none col-6 py-3 m-0 rounded-0"
                      @click="$emit('click-dismiss')">
                {{ props.labelDismissButton }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<style scoped>
.v-enter-from,
.v-leave-to {
  opacity: 0;
}

.v-enter-active,
.v-leave-active {
  transition: opacity .15s linear;
}
</style>