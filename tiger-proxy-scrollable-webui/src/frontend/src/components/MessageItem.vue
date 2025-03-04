<!--

    Copyright 2025 gematik GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<script setup lang="ts">
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import type { MetaMessageDto } from "@/api/MessageTypes.ts";
import { computed, type DeepReadonly, ref, type Ref } from "vue";
import { useResizeObserver } from "@vueuse/core";
import ShareOutlined from "@/assets/share-outlined.svg";
import { faShare } from "@fortawesome/free-solid-svg-icons";

const props = defineProps<{
  message: DeepReadonly<MetaMessageDto>;
  onHeightChanged: (height: number) => void;
  onClick: () => void;
}>();
const { message } = props;

const el: Ref<HTMLElement | null> = ref(null);

useResizeObserver(el, (entries) => {
  const h = entries[0].contentRect.height;
  if (h > 0) {
    props.onHeightChanged(h);
  }
});

function extractTimeFromISO(isoDatetime: string): string {
  if (isoDatetime) {
    const dt = new Date(isoDatetime);
    return dt.toLocaleTimeString() + "." + dt.getMilliseconds();
  } else {
    return "";
  }
}

const seqLabel = computed(() => {
  let out = `${message.sequenceNumber + 1}`;
  if (message.pairedSequenceNumber >= 0) {
    out += "\u00A0";
    out += message.request ? "⇢" : "⇠";
    out += "\u00A0";
    out += `${message.pairedSequenceNumber + 1}`;
  }
  return out;
});
</script>

<template>
  <div ref="el" class="message-item text-muted d-flex flex-column gap-2" @click="props.onClick()">
    <div class="d-flex align-items-center gap-2 flex-nowrap f-caption">
      <div class="sequence-number p-1 rounded-3 text-center">#{{ seqLabel }}</div>
      <div>
        {{ message.request ? "Request" : "Response" }} {{ extractTimeFromISO(message.timestamp) }}
      </div>
    </div>
    <div class="grid">
      <FontAwesomeIcon :icon="faShare" class="request align-self-center" v-if="message.request" />
      <ShareOutlined class="response align-self-center" v-if="!message.request" />
      <div class="text-ellipsis ms-2 align-self-center">{{ message.infoString }}</div>
      <div
        class="add-info-strings text-ellipsis ms-2 f-caption"
        v-for="additionInfoString in message.additionalInfoStrings"
        :key="additionInfoString"
      >
        &#x2937; {{ additionInfoString }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-item {
  cursor: pointer;

  .grid {
    display: grid;
    grid-template-columns: auto 1fr;
    grid-template-rows: auto;

    .request {
      color: #0298cf !important;
    }

    .response {
      color: #408509 !important;

      height: 1em;
      width: 1em;
    }

    .add-info-strings {
      grid-column: 2;
    }
  }
}

.sequence-number {
  background: var(--gem-primary-100);
}
</style>
