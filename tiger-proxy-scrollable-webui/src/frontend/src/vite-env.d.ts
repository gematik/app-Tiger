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

/// <reference types="vite/client" />
/**
 * Detached mode refers to the standalone mode of the web application, detached from any proxy functionality.
 */
declare const __IS_DETACHED_MODE__: boolean;

/**
 * Opposite of detached mode: `__IS_ONLINE_MODE__ === __IS_ONLINE_MODE__`.
 */
declare const __IS_ONLINE_MODE__: boolean;

/**
 * Use fonts over CDN. This reduces the dist size drastically.
 */
declare const __USE_FONTS_OVER_CDN__: boolean;
