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

import { type InjectionKey, onMounted, ref, type Ref } from "vue";
import { Toast } from "bootstrap";

export const toastSymbol: InjectionKey<UseToastReturn> = Symbol("toastSymbol");

export interface UseToastReturn {
  handle: Ref<Toast | null>;
  message: Ref<string>;
  showToast: (message: string) => void;
  isShown: () => boolean;
}

export function useToast(toastElement: Ref<HTMLElement | null>): UseToastReturn {
  const handle: Ref<Toast | null> = ref(null);
  const message: Ref<string> = ref("");

  onMounted(() => {
    if (toastElement.value) {
      handle.value = new Toast(toastElement.value, { delay: 10000 });
    }
  });

  const showToast = (msg: string) => {
    message.value = msg;
    handle.value?.show();
  };

  return {
    handle,
    message,
    showToast,
    isShown: () => handle.value?.isShown() ?? false,
  };
}
