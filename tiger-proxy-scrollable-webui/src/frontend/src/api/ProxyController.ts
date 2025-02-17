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

import type {
  GetAllMessagesDto,
  GetMessagesDto,
  JexlQueryResponseDto,
  RbelTreeResponseDto,
  RouteDto,
  SearchMessagesDto,
  TestFilterMessagesDto,
} from "./MessageTypes.ts";
import { ProxyError, ProxyRepository } from "./ProxyRepository.ts";

export interface UseProxyControllerReturn {
  getMetaMessages: (
    props: {
      filterRbelPath?: string;
    },
    options?: ControllerCallOptions,
  ) => Promise<GetAllMessagesDto | void>;
  getMessages: (
    props: {
      fromOffset: number;
      toOffsetExcluding: number;
      filterRbelPath?: string;
      signal: AbortSignal;
    },
    options?: ControllerCallOptions,
  ) => Promise<GetMessagesDto | void>;
  resetMessageQueue: (options?: ControllerCallOptions) => Promise<void>;
  quitProxy: (options?: ControllerCallOptions) => Promise<void>;
  testFilter: (
    props: {
      rbelPath: string;
      signal: AbortSignal;
    },
    options?: ControllerCallOptions,
  ) => Promise<TestFilterMessagesDto | void>;
  searchMessages: (
    props: {
      filterRbelPath: string;
      searchRbelPath: string;
      signal: AbortSignal;
    },
    options?: ControllerCallOptions,
  ) => Promise<SearchMessagesDto | void>;
  testRbelJexlQuery: (
    props: {
      messageUuid: string;
      query: string;
    },
    options?: ControllerCallOptions,
  ) => Promise<JexlQueryResponseDto | void>;
  testRbelTreeQuery: (
    props: {
      messageUuid: string;
      query: string;
    },
    options?: ControllerCallOptions,
  ) => Promise<RbelTreeResponseDto | void>;
  importRbelLogFile: (
    props: { rbelFileContent: string },
    options?: ControllerCallOptions,
  ) => Promise<void>;
  getProxyRoutes: (options?: ControllerCallOptions) => Promise<RouteDto[] | void>;
  deleteProxyRoute: (props: { id: string }, options?: ControllerCallOptions) => Promise<void>;
  addProxyRoute: (
    props: { route: RouteDto },
    options?: ControllerCallOptions,
  ) => Promise<RouteDto | void>;
}

export interface UseProxyControllerOptions {
  onError?: (errorMessage: string, errorCode: number) => void;
  onLoading?: (isLoading: boolean) => void;
}

export interface ControllerCallOptions {
  /**
   * Supress error from being propagated to `onError`. Defaults to `false`.
   */
  suppressError?: boolean;
  /**
   * Propagate error to the caller. Defaults to `false`.
   */
  propagateError?: boolean;
}

export function useProxyController(props: UseProxyControllerOptions): UseProxyControllerReturn {
  const handleError = (err: any, options?: ControllerCallOptions) => {
    if (options?.suppressError === undefined || options?.suppressError === false) {
      if (err instanceof ProxyError) {
        if (props.onError) props.onError(err.message, err.response.status);
      }
    }
    if (options?.propagateError === true) {
      throw err;
    }
  };

  const makeCall = async <T>(
    call: () => Promise<T>,
    options?: ControllerCallOptions,
  ): Promise<T | void> => {
    try {
      if (props.onLoading) props.onLoading(true);
      return await call();
    } catch (err) {
      handleError(err, options);
    } finally {
      if (props.onLoading) props.onLoading(false);
    }
  };

  return {
    getMetaMessages: (props, options) =>
      makeCall(() => ProxyRepository.fetchMessagesWithMeta(props), options),
    getMessages: (props, options) =>
      makeCall(() => ProxyRepository.fetchMessagesWithHtml(props), options),
    resetMessageQueue: (options) => makeCall(() => ProxyRepository.fetchResetMessages(), options),
    quitProxy: (options) => makeCall(() => ProxyRepository.fetchQuitProxy(), options),
    testFilter: (props, options) => makeCall(() => ProxyRepository.fetchTestFilter(props), options),
    searchMessages: (props, options) =>
      makeCall(() => ProxyRepository.searchMessages(props), options),
    testRbelJexlQuery: (props, options) =>
      makeCall(() => ProxyRepository.fetchTestJexlQuery(props), options),
    testRbelTreeQuery: (props, options) =>
      makeCall(() => ProxyRepository.fetchTestRbelTreeQuery(props), options),
    importRbelLogFile: (props, options) =>
      makeCall(() => ProxyRepository.fetchImportTraffic(props), options),
    getProxyRoutes: (options) => makeCall(() => ProxyRepository.fetchAllProxyRoutes(), options),
    deleteProxyRoute: (props, options) =>
      makeCall(() => ProxyRepository.fetchDeleteProxyRoute(props), options),
    addProxyRoute: (props, options) =>
      makeCall(() => ProxyRepository.fetchAddProxyRoute(props), options),
  };
}
