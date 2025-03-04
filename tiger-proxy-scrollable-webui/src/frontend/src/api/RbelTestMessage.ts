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

import { type DeepReadonly, readonly, ref, type Ref } from "vue";
import { useProxyController, type UseProxyControllerOptions } from "./ProxyController.ts";
import type { JexlQueryResponseDto, RbelTreeResponseDto } from "./MessageTypes.ts";
import type { Message } from "@/api/MessageQueue.ts";

export interface UseRbelTestMessageReturn {
  isLoading: DeepReadonly<Ref<boolean>>;

  rbelPathTestResult: DeepReadonly<Ref<JexlQueryResponseDto | null>>;

  testRbelPathQuery: () => void;

  resetTestResult: () => void;
}

export interface UseRbelTestMessageOptions extends UseProxyControllerOptions {}

export function useRbelTestMessage(
  message: Ref<Message>,
  rbelPath: Ref<string>,
  props: UseRbelTestMessageOptions,
): UseRbelTestMessageReturn {
  const { testRbelJexlQuery } = useProxyController(props);
  const isLoading: Ref<boolean> = ref(false);
  const rbelPathTestResult: Ref<JexlQueryResponseDto | null> = ref(null);

  async function handleTest(fetchFn: () => Promise<void>) {
    try {
      isLoading.value = true;
      await fetchFn();
    } finally {
      isLoading.value = false;
    }
  }

  const resetTestResult = () => {
    isLoading.value = false;
    rbelPathTestResult.value = null;
  };

  const testRbelPathQuery = () => {
    handleTest(async () => {
      rbelPath.value = rbelPath.value.trim();
      const result = await testRbelJexlQuery({
        messageUuid: message.value.uuid,
        query: rbelPath.value,
      });
      if (result) {
        rbelPathTestResult.value = result;
      }
    });
  };

  return {
    isLoading: readonly(isLoading),
    rbelPathTestResult: readonly(rbelPathTestResult),
    testRbelPathQuery,
    resetTestResult,
  };
}

export interface UseRbelTestTreeMessageReturn {
  isLoading: DeepReadonly<Ref<boolean>>;

  rbelPathTestResult: DeepReadonly<Ref<RbelTreeResponseDto | null>>;

  testRbelPathQuery: () => void;

  resetTestResult: () => void;
}

export interface UseRbelTestTreeMessageOptions extends UseProxyControllerOptions {}

export function useRbelTestTreeMessage(
  message: Ref<Message>,
  rbelPath: Ref<string>,
  props: UseRbelTestTreeMessageOptions,
): UseRbelTestTreeMessageReturn {
  const { testRbelTreeQuery } = useProxyController(props);
  const isLoading: Ref<boolean> = ref(false);
  const rbelTreeTestResult: Ref<RbelTreeResponseDto | null> = ref(null);

  async function handleTest(fetchFn: () => Promise<void>) {
    try {
      isLoading.value = true;
      await fetchFn();
    } finally {
      isLoading.value = false;
    }
  }

  const resetTestResult = () => {
    isLoading.value = false;
    rbelTreeTestResult.value = null;
  };

  const testRbelPathQuery = () => {
    handleTest(async () => {
      rbelPath.value = rbelPath.value.trim();
      const result = await testRbelTreeQuery({
        messageUuid: message.value.uuid,
        query: rbelPath.value,
      });
      if (result) {
        rbelTreeTestResult.value = result;
      }
    });
  };

  return {
    isLoading: readonly(isLoading),
    rbelPathTestResult: readonly(rbelTreeTestResult),
    testRbelPathQuery,
    resetTestResult,
  };
}
