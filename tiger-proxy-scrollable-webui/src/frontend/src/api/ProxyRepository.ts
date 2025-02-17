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

export class ProxyError extends Error {
  response: Response;

  constructor(response: Response) {
    super(response.statusText);
    this.response = response;
  }
}

export const ProxyRepository = {
  /**
   * Returns a list of all messages without content.
   */
  fetchMessagesWithMeta: async ({
    filterRbelPath,
  }: {
    filterRbelPath?: string;
  }): Promise<GetAllMessagesDto> => {
    const params = new URLSearchParams();
    if (filterRbelPath) params.set("filterRbelPath", filterRbelPath);
    const result = await fetch(`/api/getMessagesWithMeta?${params.toString()}`);
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as GetAllMessagesDto;
  },

  /**
   * Returns a list of messages with html content.
   */
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
    const result = await fetch(`/api/getMessagesWithHtml?${params.toString()}`, {
      signal,
    });
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as GetMessagesDto;
  },

  /**
   * Request to reset the message queue in the backend.
   */
  fetchResetMessages: async (): Promise<void> => {
    const result = await fetch(`/api/resetMessages`);
    if (!result.ok) throw new ProxyError(result);
  },

  /**
   * Request to quit the backend.
   */
  fetchQuitProxy: async (): Promise<void> => {
    const result = await fetch(`/api/quit`);
    if (!result.ok) throw new ProxyError(result);
  },

  /**
   * Test the rbel expression.
   */
  fetchTestFilter: async ({
    rbelPath,
    signal,
  }: {
    rbelPath: string;
    signal: AbortSignal;
  }): Promise<TestFilterMessagesDto> => {
    const params = new URLSearchParams();
    params.set("filterRbelPath", rbelPath);
    const result = await fetch(`/api/testFilterMessages?${params.toString()}`, {
      signal,
    });
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as TestFilterMessagesDto;
  },

  /**
   * Search messages by rbel expression.
   */
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
    const result = await fetch(`/api/searchMessages?${params.toString()}`, {
      signal,
    });
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as SearchMessagesDto;
  },

  /**
   * Test the jexl/rbel expression on a specific message.
   */
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
    const result = await fetch(`/api/testJexlQuery?${params.toString()}`);
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as JexlQueryResponseDto;
  },

  /**
   * Get partial rbel tree of a message matching the query.
   */
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
    const result = await fetch(`/api/testRbelExpression?${params.toString()}`);
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as RbelTreeResponseDto;
  },

  /**
   * Import rbel traffic log file.
   */
  fetchImportTraffic: async ({ rbelFileContent }: { rbelFileContent: string }): Promise<void> => {
    const result = await fetch(`/api/importTraffic`, {
      method: "POST",
      body: rbelFileContent,
    });
    if (!result.ok) throw new ProxyError(result);
  },

  fetchAllProxyRoutes: async (): Promise<RouteDto[]> => {
    const result = await fetch(`/api/route`);
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as RouteDto[];
  },

  fetchAddProxyRoute: async ({ route }: { route: RouteDto }): Promise<RouteDto> => {
    const result = await fetch(`/api/route`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(route),
    });
    if (!result.ok) throw new ProxyError(result);
    return (await result.json()) as RouteDto;
  },

  fetchDeleteProxyRoute: async ({ id }: { id: string }): Promise<void> => {
    const result = await fetch(`/api/route/${id}`, {
      method: "DELETE",
    });
    if (!result.ok) throw new ProxyError(result);
  },
};
