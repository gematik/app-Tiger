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

import { type DeepReadonly, readonly, ref, type Ref } from "vue";
import { useDebounceFn } from "@vueuse/core";
import { useProxyController, type UseProxyControllerOptions } from "./ProxyController.ts";
import type { TestFilterMessagesDto } from "./MessageTypes.ts";
import { ProxyError } from "@/api/ProxyRepository.ts";

export interface UseRbelTestFilterReturn {
  isLoading: DeepReadonly<Ref<boolean>>;
  rbelTestResult: DeepReadonly<Ref<TestFilterMessagesDto | null>>;
  testRbel: (rbel: string) => void;
  resetTestResult: () => void;
}

export interface UseRbelTestFilterOptions extends UseProxyControllerOptions {}

export function useRbelTestFilter(props: UseRbelTestFilterOptions): UseRbelTestFilterReturn {
  const { testFilter } = useProxyController(props);
  const isLoading: Ref<boolean> = ref(false);
  const rbelTestResult: Ref<TestFilterMessagesDto | null> = ref(null);

  let abortController = new AbortController();
  async function handleTestFilter(rbelPath: string) {
    try {
      abortController.abort();
      abortController = new AbortController();
      isLoading.value = true;
      const result = await testFilter(
        { rbelPath, signal: abortController.signal },
        { suppressError: true, propagateError: true },
      );
      if (result) {
        rbelTestResult.value = result;
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        // noop; expected
      } else if (props.onError) {
        if (err instanceof ProxyError) {
          props.onError(err.message, err.response.status);
        } else {
          props.onError((err as Error).message, -1);
        }
      }
    } finally {
      isLoading.value = false;
    }
  }

  const testRbel = useDebounceFn(async (rbelPath) => {
    resetTestResult();
    await handleTestFilter(rbelPath);
  }, 300);

  const resetTestResult = () => {
    isLoading.value = false;
    rbelTestResult.value = null;
  };

  return {
    isLoading: readonly(isLoading),
    rbelTestResult: readonly(rbelTestResult),
    testRbel,
    resetTestResult,
  };
}
