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

import { type InjectionKey, type Ref } from "vue";
import { useStorage } from "@vueuse/core";

export const settingsSymbol: InjectionKey<SettingsReturn> = Symbol("settingsSymbol");

export interface SettingsReturn {
  reverseMessageQueue: Ref<boolean>;
  hideMessageHeaders: Ref<boolean>;
  hideMessageDetails: Ref<boolean>;
}

export function useSettings(): SettingsReturn {
  const reverseMessageQueue = useStorage("reverseMessageQueue", false);
  const hideMessageHeaders = useStorage("hideMessageHeaders", false);
  const hideMessageDetails = useStorage("hideMessageDetails", false);

  return {
    reverseMessageQueue,
    hideMessageHeaders,
    hideMessageDetails,
  };
}
