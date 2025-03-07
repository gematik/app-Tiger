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

import type { RouteDto } from "./MessageTypes.ts";
import { getProxy, ProxyError, type ProxyRepository } from "./ProxyRepository.ts";

export type ControllerCall<Fn extends (...args: any) => any> = (
  props: Parameters<Fn>[0] & { signal?: AbortSignal },
  options?: ControllerCallOptions,
) => Promise<Awaited<ReturnType<Fn>> | void>;

export interface UseProxyControllerReturn {
  getMetaMessages: ControllerCall<ProxyRepository["fetchMessagesWithMeta"]>;
  getMessages: ControllerCall<ProxyRepository["fetchMessagesWithHtml"]>;
  resetMessageQueue: (options?: ControllerCallOptions) => Promise<void>;
  quitProxy: (options?: ControllerCallOptions) => Promise<void>;
  testFilter: ControllerCall<ProxyRepository["fetchTestFilter"]>;
  searchMessages: ControllerCall<ProxyRepository["searchMessages"]>;
  testRbelJexlQuery: ControllerCall<ProxyRepository["fetchTestJexlQuery"]>;
  testRbelTreeQuery: ControllerCall<ProxyRepository["fetchTestRbelTreeQuery"]>;
  importRbelLogFile: ControllerCall<ProxyRepository["fetchImportTraffic"]>;
  downloadRbelLogFile: ControllerCall<ProxyRepository["fetchDownloadTraffic"]>;
  getProxyRoutes: (options?: ControllerCallOptions) => Promise<RouteDto[] | void>;
  deleteProxyRoute: ControllerCall<ProxyRepository["fetchDeleteProxyRoute"]>;
  addProxyRoute: ControllerCall<ProxyRepository["fetchAddProxyRoute"]>;
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

const proxyRepo = getProxy();

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
      makeCall(() => proxyRepo.fetchMessagesWithMeta(props), options),
    getMessages: (props, options) =>
      makeCall(() => proxyRepo.fetchMessagesWithHtml(props), options),
    resetMessageQueue: (options) => makeCall(() => proxyRepo.fetchResetMessages(), options),
    quitProxy: (options) => makeCall(() => proxyRepo.fetchQuitProxy(), options),
    testFilter: (props, options) => makeCall(() => proxyRepo.fetchTestFilter(props), options),
    searchMessages: (props, options) => makeCall(() => proxyRepo.searchMessages(props), options),
    testRbelJexlQuery: (props, options) =>
      makeCall(() => proxyRepo.fetchTestJexlQuery(props), options),
    testRbelTreeQuery: (props, options) =>
      makeCall(() => proxyRepo.fetchTestRbelTreeQuery(props), options),
    importRbelLogFile: (props, options) =>
      makeCall(() => proxyRepo.fetchImportTraffic(props), options),
    downloadRbelLogFile: (props, options) =>
      makeCall(() => proxyRepo.fetchDownloadTraffic(props), options),
    getProxyRoutes: (options) => makeCall(() => proxyRepo.fetchAllProxyRoutes(), options),
    deleteProxyRoute: (props, options) =>
      makeCall(() => proxyRepo.fetchDeleteProxyRoute(props), options),
    addProxyRoute: (props, options) => makeCall(() => proxyRepo.fetchAddProxyRoute(props), options),
  };
}
