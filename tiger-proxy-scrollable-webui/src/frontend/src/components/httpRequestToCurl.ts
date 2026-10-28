///
///
/// Copyright 2021-2026 gematik GmbH
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

import type { HttpRequest } from "./httpRequestParser";

export type CurlShell = "bash" | "cmd";

function quoteCurlArgument(value: string, shell: CurlShell): string {
  const containsNullByte = value.includes(String.fromCodePoint(0));
  if (containsNullByte) {
    throw new Error("Shell arguments cannot contain a NUL character.");
  }
  if (shell === "bash") {
    return "'" + value.replaceAll("'", String.raw`'\''`) + "'";
  }
  const windowsArgument = escapeWindowsArgument(value);
  // Like Chrome, cmd continuation preserves LF using two physical newlines, but normalizes CRLF.
  const escapedArgument = windowsArgument
    .replace(/["^&|<>()!%]/g, "^$&")
    .replace(/%(?=[A-Za-z0-9_])/g, "%^")
    .replace(/\r\n|\r|\n/g, "^\n\n");
  return '^"' + escapedArgument + '^"';
}

function escapeWindowsArgument(value: string): string {
  const backslash = String.fromCodePoint(92);
  let escaped = "";
  let backslashes = 0;
  for (const character of value) {
    if (character === backslash) {
      backslashes++;
      continue;
    }
    if (character === '"') {
      escaped += backslash.repeat(backslashes * 2 + 1) + character;
    } else {
      escaped += backslash.repeat(backslashes) + character;
    }
    backslashes = 0;
  }
  return escaped + backslash.repeat(backslashes * 2);
}

function convertToCurlHeaders(headers: Map<string, string[]>, shell: CurlShell = "bash"): string {
  return Array.from(headers)
    .filter(([key]) => key.toLowerCase() !== "content-length")
    .flatMap(([key, values]) =>
      values.map((value) => {
        const header = value.trim() ? `${key}:${value}` : `${key};`;
        return ` -H ${quoteCurlArgument(header, shell)}`;
      }),
    )
    .join("");
}

function getHttpUrl(request: HttpRequest, protocol: "http" | "https"): string {
  if (/^https?:\/\//i.test(request.target)) {
    return request.target;
  }
  const host = Array.from(request.headers)
    .find(([name]) => name.toLowerCase() === "host")?.[1][0]
    ?.trim();
  if (request.target.startsWith("/") && host) {
    return `${protocol}://${host}${request.target}`;
  }
  throw new Error("An HTTP URL or a request path with a Host header is required.");
}

function validateRequestEncoding(headers: Map<string, string[]>): void {
  if (
    Array.from(headers).some(
      ([name, values]) =>
        ["content-encoding", "transfer-encoding"].includes(name.toLowerCase()) &&
        values.some((value) => value.trim().toLowerCase() !== "identity"),
    )
  ) {
    throw new Error("Compressed or transfer-encoded bodies are not supported yet.");
  }
}

function convertToCurlBody(request: HttpRequest, shell: CurlShell): string {
  if (!request.body) return "";
  const hasContentType = Array.from(request.headers.keys()).some(
    (name) => name.toLowerCase() === "content-type",
  );
  const contentTypeArgument = hasContentType
    ? ""
    : ` -H ${quoteCurlArgument("Content-Type:", shell)}`;
  return ` --data-raw ${quoteCurlArgument(request.body, shell)}${contentTypeArgument}`;
}

export function convertToCurl(
  httpMessage: HttpRequest,
  shell: CurlShell = "bash",
  protocol: "http" | "https" = "http",
): string {
  validateRequestEncoding(httpMessage.headers);
  if (httpMessage.method.toUpperCase() === "CONNECT") {
    throw new Error("CONNECT requests are not supported.");
  }

  const command =
    "curl -v --globoff --path-as-is" +
    (httpMessage.httpVersion === "2.0" ? " --http2-prior-knowledge" : "") +
    ` -X ${quoteCurlArgument(httpMessage.method, shell)}` +
    ` --url ${quoteCurlArgument(getHttpUrl(httpMessage, protocol), shell)}` +
    convertToCurlHeaders(httpMessage.headers, shell) +
    convertToCurlBody(httpMessage, shell);
  if (shell === "cmd" && command.length > 8191) {
    throw new Error("This request is too large for CMD. Try Bash instead.");
  }
  return command;
}
