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

/**
 * Enum for different types of mismatch notes, matching the backend RbelMismatchNoteFacet.MismatchType
 */
export enum MismatchType {
  VALUE_MISMATCH = "VALUE_MISMATCH",
  MISSING_NODE = "MISSING_NODE",
  WRONG_PATH = "WRONG_PATH",
  FILTER_MISMATCH = "FILTER_MISMATCH",
  AMBIGUOUS = "AMBIGUOUS",
  UNKNOWN = "UNKNOWN",
}

/**
 * Represents a mismatch note with details about the mismatch detected during test execution.
 */
export interface IMismatchNote {
  /** The type of mismatch */
  mismatchType: MismatchType;
  /** The sequence number of the related message */
  sequenceNumber: number;
  /** The RBEL path where the mismatch occurred */
  rbelPath: string;
  /** A descriptive value or message for the mismatch */
  value: string;
}
