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
import { useDebounceFn } from "@vueuse/core";
import { useProxyController, type UseProxyControllerOptions } from "./ProxyController.ts";
import type { SearchMessagesDto } from "./MessageTypes.ts";
import { ProxyError } from "@/api/ProxyRepository.ts";

export interface SearchMessagesReturn {
  isLoading: DeepReadonly<Ref<boolean>>;
  searchResult: DeepReadonly<Ref<SearchMessagesDto | null>>;
  search: (rbel: string) => void;
  resetSearch: () => void;
}

export interface SearchMessagesOptions extends UseProxyControllerOptions {}

export function useSearchMessages(
  currentFilterRbelPath: Ref<string>,
  props: SearchMessagesOptions,
): SearchMessagesReturn {
  const { searchMessages } = useProxyController(props);
  const isLoading: Ref<boolean> = ref(false);
  const searchResult: Ref<SearchMessagesDto | null> = ref(null);

  let abortController = new AbortController();
  async function handleSearch(searchRbelPath: string) {
    try {
      abortController.abort();
      abortController = new AbortController();
      isLoading.value = true;
      const result = await searchMessages(
        {
          filterRbelPath: currentFilterRbelPath.value,
          searchRbelPath,
          signal: abortController.signal,
        },
        { suppressError: true, propagateError: true },
      );
      if (result) {
        searchResult.value = result;
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

  const search = useDebounceFn(async (rbelPath) => {
    resetSearch();
    await handleSearch(rbelPath);
  }, 300);

  const resetSearch = () => {
    isLoading.value = false;
    searchResult.value = null;
  };

  return {
    isLoading: readonly(isLoading),
    searchResult: readonly(searchResult),
    search,
    resetSearch,
  };
}
