///
///
/// Copyright 2025 gematik GmbH
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

import { type InjectionKey, ref, type Ref } from "vue";
import type { Message } from "@/api/MessageQueue.ts";

export const rawContentModalSymbol: InjectionKey<UseRawContentModalReturn> =
  Symbol("rawContentModalSymbol");

export interface UseRawContentModalReturn {
  selected: Ref<{ message: Message; rawContent: string } | null>;
  show: (message: Message, rawContent: string) => void;
  hide: () => void;
}

export function useRawContentModal(): UseRawContentModalReturn {
  const selected: Ref<{ message: Message; rawContent: string } | null> = ref(null);

  const show = (message: Message, rawContent: string) => {
    selected.value = { message, rawContent };
  };

  const hide = () => {
    selected.value = null;
  };

  return {
    selected,
    show,
    hide,
  };
}
