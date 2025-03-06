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
import type { DetachedRbelLog, WindowExt } from "@/WindowExt.ts";
import { inflateSync } from "fflate";

export class ProxyError extends Error {
  response: Response;

  constructor(response: Response) {
    super(response.statusText);
    this.response = response;
  }
}

export function getProxy(): ProxyRepository {
  return __IS_DETACHED_MODE__ ? ProxyRepositoryLocal : ProxyRepositoryRemote;
}

export type ProxyRepository = {
  fetchMessagesWithMeta(props: { filterRbelPath?: string }): Promise<GetAllMessagesDto>;

  fetchMessagesWithHtml(props: {
    fromOffset: number;
    toOffsetExcluding: number;
    filterRbelPath?: string;
    signal: AbortSignal;
  }): Promise<GetMessagesDto>;

  fetchResetMessages(): Promise<void>;

  fetchQuitProxy(): Promise<void>;

  fetchTestFilter(props: { rbelPath: string; signal: AbortSignal }): Promise<TestFilterMessagesDto>;

  searchMessages(props: {
    filterRbelPath: string;
    searchRbelPath: string;
    signal: AbortSignal;
  }): Promise<SearchMessagesDto>;

  fetchTestJexlQuery(props: { messageUuid: string; query: string }): Promise<JexlQueryResponseDto>;

  fetchTestRbelTreeQuery(props: {
    messageUuid: string;
    query: string;
  }): Promise<RbelTreeResponseDto>;

  fetchImportTraffic(props: { rbelFileContent: string }): Promise<void>;
  fetchDownloadTraffic(props: { suffix: string; filterRbelPath?: string }): Promise<string>;

  fetchAllProxyRoutes(): Promise<RouteDto[]>;

  fetchAddProxyRoute(props: { route: RouteDto }): Promise<RouteDto>;

  fetchDeleteProxyRoute(props: { id: string }): Promise<void>;
};

function throwNotImplemented(): never {
  throw new Error("Not implemented");
}

let resolvedLog: DetachedRbelLog | null = null;

async function getDetachedTigerLog(): Promise<DetachedRbelLog | null> {
  if (!resolvedLog) {
    const dataUrl = (window as unknown as WindowExt).__TGR_RBEL_LOG__;
    if (dataUrl) {
      const response = await fetch(dataUrl);
      const compressed = await response.arrayBuffer();
      const decompressed = inflateSync(new Uint8Array(compressed));
      resolvedLog = JSON.parse(new TextDecoder().decode(decompressed)) as DetachedRbelLog;
    } else {
      return null;
    }
  }
  return resolvedLog;
}

const ProxyRepositoryLocal: ProxyRepository = {
  async fetchMessagesWithHtml(props: {
    fromOffset: number;
    toOffsetExcluding: number;
    filterRbelPath?: string;
  }): Promise<GetMessagesDto> {
    const messagesWithHtml = (await getDetachedTigerLog())!.messagesWithHtml;
    return Promise.resolve({
      ...messagesWithHtml,
      messages: messagesWithHtml.messages.slice(props.fromOffset, props.toOffsetExcluding),
      fromOffset: props.fromOffset,
      toOffsetExcluding: props.toOffsetExcluding,
    });
  },
  async fetchMessagesWithMeta(): Promise<GetAllMessagesDto> {
    const messagesWithMeta = (await getDetachedTigerLog())!.messagesWithMeta;
    return Promise.resolve(messagesWithMeta);
  },

  fetchQuitProxy(): Promise<void> {
    throwNotImplemented();
  },
  fetchResetMessages(): Promise<void> {
    throwNotImplemented();
  },
  fetchTestFilter(): Promise<TestFilterMessagesDto> {
    throwNotImplemented();
  },
  fetchTestJexlQuery(): Promise<JexlQueryResponseDto> {
    throwNotImplemented();
  },
  fetchTestRbelTreeQuery(): Promise<RbelTreeResponseDto> {
    throwNotImplemented();
  },
  searchMessages(): Promise<SearchMessagesDto> {
    throwNotImplemented();
  },
  fetchAddProxyRoute(): Promise<RouteDto> {
    throwNotImplemented();
  },
  fetchAllProxyRoutes(): Promise<RouteDto[]> {
    throwNotImplemented();
  },
  fetchDeleteProxyRoute(): Promise<void> {
    throwNotImplemented();
  },
  fetchImportTraffic(): Promise<void> {
    throwNotImplemented();
  },
  fetchDownloadTraffic(): Promise<string> {
    throwNotImplemented();
  },
};

async function createFetchRequest<T>(url: string, options: RequestInit = {}): Promise<T> {
  const result = await fetch(url, options);
  if (!result.ok) throw new ProxyError(result);
  const contentType = result.headers.get("Content-Type");
  if (contentType && contentType.includes("application/json")) {
    return (await result.json()) as T;
  } else {
    return (await result.text()) as unknown as T;
  }
}

const ProxyRepositoryRemote: ProxyRepository = {
  fetchMessagesWithMeta: async ({
    filterRbelPath,
  }: {
    filterRbelPath?: string;
  }): Promise<GetAllMessagesDto> => {
    const params = new URLSearchParams();
    if (filterRbelPath) params.set("filterRbelPath", filterRbelPath);
    return createFetchRequest<GetAllMessagesDto>(`/webui/getMessagesWithMeta?${params.toString()}`);
  },

  fetchMessagesWithHtml: async ({
    fromOffset,
    toOffsetExcluding,
    filterRbelPath,
    signal,
  }: {
    fromOffset: number;
    toOffsetExcluding: number;
    filterRbelPath?: string;
    signal: AbortSignal;
  }): Promise<GetMessagesDto> => {
    const params = new URLSearchParams();
    params.set("fromOffset", fromOffset.toString());
    params.set("toOffsetExcluding", toOffsetExcluding.toString());
    if (filterRbelPath) params.set("filterRbelPath", filterRbelPath);
    return createFetchRequest<GetMessagesDto>(`/webui/getMessagesWithHtml?${params.toString()}`, {
      signal,
    });
  },

  fetchResetMessages: async (): Promise<void> => {
    await createFetchRequest<void>(`/webui/resetMessages`);
  },

  fetchQuitProxy: async (): Promise<void> => {
    await createFetchRequest<void>(`/webui/quit`);
  },

  fetchTestFilter: async ({
    rbelPath,
    signal,
  }: {
    rbelPath: string;
    signal: AbortSignal;
  }): Promise<TestFilterMessagesDto> => {
    const params = new URLSearchParams();
    params.set("filterRbelPath", rbelPath);
    return createFetchRequest<TestFilterMessagesDto>(
      `/webui/testFilterMessages?${params.toString()}`,
      { signal },
    );
  },

  searchMessages: async ({
    filterRbelPath,
    searchRbelPath,
    signal,
  }: {
    filterRbelPath: string;
    searchRbelPath: string;
    signal: AbortSignal;
  }): Promise<SearchMessagesDto> => {
    const params = new URLSearchParams();
    params.set("filterRbelPath", filterRbelPath);
    params.set("searchRbelPath", searchRbelPath);
    return createFetchRequest<SearchMessagesDto>(`/webui/searchMessages?${params.toString()}`, {
      signal,
    });
  },

  fetchTestJexlQuery: async ({
    messageUuid,
    query,
  }: {
    messageUuid: string;
    query: string;
  }): Promise<JexlQueryResponseDto> => {
    const params = new URLSearchParams();
    params.set("messageUuid", messageUuid);
    params.set("query", query);
    return createFetchRequest<JexlQueryResponseDto>(`/webui/testJexlQuery?${params.toString()}`);
  },

  fetchTestRbelTreeQuery: async ({
    messageUuid,
    query,
  }: {
    messageUuid: string;
    query: string;
  }): Promise<RbelTreeResponseDto> => {
    const params = new URLSearchParams();
    params.set("messageUuid", messageUuid);
    params.set("query", query);
    return createFetchRequest<RbelTreeResponseDto>(
      `/webui/testRbelExpression?${params.toString()}`,
    );
  },

  fetchImportTraffic: async ({ rbelFileContent }: { rbelFileContent: string }): Promise<void> => {
    await createFetchRequest<void>(`/webui/importTraffic`, {
      method: "POST",
      body: rbelFileContent,
    });
  },

  fetchDownloadTraffic: async ({
    suffix,
    filterRbelPath,
  }: {
    suffix: string;
    filterRbelPath?: string;
  }): Promise<string> => {
    const params = new URLSearchParams();
    if (filterRbelPath) params.set("filterRbelPath", filterRbelPath);
    return createFetchRequest<string>(`/webui/trafficLog_${suffix}.tgr?${params}`, {
      method: "GET",
    });
  },

  fetchAllProxyRoutes: async (): Promise<RouteDto[]> => {
    return createFetchRequest<RouteDto[]>(`/route`);
  },

  fetchAddProxyRoute: async ({ route }: { route: RouteDto }): Promise<RouteDto> => {
    return createFetchRequest<RouteDto>(`/route`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(route),
    });
  },

  fetchDeleteProxyRoute: async ({ id }: { id: string }): Promise<void> => {
    await createFetchRequest<void>(`/route/${id}`, {
      method: "DELETE",
    });
  },
};
