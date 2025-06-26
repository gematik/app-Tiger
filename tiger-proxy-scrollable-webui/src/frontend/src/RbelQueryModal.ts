///
///
/// Copyright 2021-2025 gematik GmbH
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

import { type InjectionKey, ref, type Ref } from "vue";
import type { Message } from "@/api/MessageQueue.ts";

export const rbelQueryModalSymbol: InjectionKey<UseRbelQueryModalReturn> =
  Symbol("rbelQueryModalSymbol");

export interface UseRbelQueryModalReturn {
  selectedMessage: Ref<Message | null>;
  show: (message: Message) => void;
  hide: () => void;
}

export function useRbelQueryModal(): UseRbelQueryModalReturn {
  const selectedMessage: Ref<Message | null> = ref(null);

  const show = (message: Message) => {
    selectedMessage.value = message;
  };

  const hide = () => {
    selectedMessage.value = null;
  };

  return {
    selectedMessage,
    show,
    hide,
  };
}
