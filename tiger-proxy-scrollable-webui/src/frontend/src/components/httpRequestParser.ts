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

export type HttpRequest = {
  method: string;
  target: string;
  headers: Map<string, string[]>;
  body: string;
  httpVersion?: "1.0" | "1.1" | "2.0";
};

export function parseHttpRequest(rawRequest: string): HttpRequest {
  if (!rawRequest.trim()) {
    throw new Error("The raw HTTP request is empty.");
  }
  const separator = /\r?\n\r?\n/.exec(rawRequest);
  if (!separator) {
    throw new Error("The HTTP request is missing the header/body separator.");
  }
  const head = rawRequest.slice(0, separator.index);
  const lines = head.split(/\r?\n/);
  const requestLine = lines.shift() ?? "";
  const requestParts = /^([!#$%&'*+.^_`|~0-9A-Za-z-]+) (\S+) HTTP\/(1\.[01]|2\.0)$/.exec(requestLine);
  if (!requestParts) {
    throw new Error("Expected an HTTP/1.0, HTTP/1.1 or HTTP/2.0 request line.");
  }
  let httpVersion: HttpRequest["httpVersion"] = "1.1";
  if (requestParts[3] === "2.0") {
    httpVersion = "2.0";
  } else if (requestParts[3] === "1.0") {
    httpVersion = "1.0";
  }
  return {
    method: requestParts[1],
    target: requestParts[2],
    headers: parseHttpHeaders(lines, head.includes("\r\n") ? "\r\n" : "\n"),
    body: rawRequest.slice(separator.index + separator[0].length),
    httpVersion,
  };
}

function parseHttpHeaders(lines: string[], lineEnding: string): Map<string, string[]> {
  const headers = new Map<string, string[]>();
  let previousHeader: string | undefined;
  for (const line of lines) {
    if (line.startsWith(" ") || line.startsWith("\t")) {
      if (!previousHeader) {
        throw new Error("A folded HTTP header has no preceding header.");
      }
      const values = headers.get(previousHeader)!;
      values[values.length - 1] += lineEnding + line;
      continue;
    }
    const colon = line.indexOf(":");
    const name = line.slice(0, colon);
    if (colon <= 0 || !/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(name)) {
      throw new Error("The HTTP request contains an invalid header name.");
    }
    const values = headers.get(name) ?? [];
    values.push(line.slice(colon + 1));
    headers.set(name, values);
    previousHeader = name;
  }
  return headers;
}
