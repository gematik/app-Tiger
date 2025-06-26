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

export type BaseMessagesDto<T> = {
  totalFiltered: number;
  total: number;
  filter: GetMessagesFilterDto | null;
  hash: string;
  messages: T[];
};

export type GetMessagesDto = BaseMessagesDto<HtmlMessageDto> & {
  fromOffset: number;
  toOffsetExcluding: number;
};

export type GetAllMessagesDto = BaseMessagesDto<MetaMessageDto>;

export type GetMessagesFilterDto = {
  rbelPath: string | null;
};

export type BaseMessageDto = {
  uuid: string;
  offset: number;
  sequenceNumber: number;
};

export type HtmlMessageDto = BaseMessageDto & {
  content: string;
};

export type MetaMessageDto = BaseMessageDto & {
  infoString: string;
  additionalInfoStrings: string[];
  timestamp: string;
  request: boolean;
  pairedUuid: string;
  pairedSequenceNumber: number;
  recipient: string | null;
  sender: string | null;
};

export type SearchMessagesDto = {
  totalFiltered: string | null;
  total: number;
  hash: string;
  filter: GetMessagesFilterDto;
  errorMessage: string | null;
  searchFilter: GetMessagesFilterDto;
  messages: MetaMessageDto[] | null;
};

export type TestFilterMessagesDto = Omit<SearchMessagesDto, "searchFilter" | "messages">;

export type QueryResponseDto = {
  messageUuid: string;
  query: string;
  errorMessage: string | null;
};

export type JexlQueryResponseDto = QueryResponseDto & {
  matchSuccessful: boolean | null;
  messageContext: Record<string, any> | null;
};

export type RbelTreeResponseDto = QueryResponseDto & {
  elementsWithTree: Record<string, string>[] | null;
};

export type RouteDto = {
  id: string | null;
  from: string;
  to: string;
};
