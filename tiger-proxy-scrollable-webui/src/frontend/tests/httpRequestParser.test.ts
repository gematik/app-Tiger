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

import { describe, expect, it } from "vitest";
import { parseHttpRequest } from "../src/components/httpRequestParser";

describe("parseHttpRequest", () => {
  it("extracts the method, target and original header names and values", () => {
    expect(
      parseHttpRequest(
        "POST /api?q=hello%20world HTTP/1.1\r\n" +
          "hOsT: example.org:8080\r\n" +
          "X-Test:\t  value:with:colons  \r\n\r\n" +
          '{"value":1}',
      ),
    ).toEqual({
      method: "POST",
      target: "/api?q=hello%20world",
      headers: new Map([
        ["hOsT", [" example.org:8080"]],
        ["X-Test", ["\t  value:with:colons  "]],
      ]),
      body: '{"value":1}',
      httpVersion: "1.1",
    });
  });

  it("preserves repeated values and keeps differently spelled names separate", () => {
    const request = parseHttpRequest(
      "GET / HTTP/1.1\r\nX-Test: first\r\nX-Test: second\r\nx-test: third\r\n\r\n",
    );
    expect(request.headers).toEqual(
      new Map([
        ["X-Test", [" first", " second"]],
        ["x-test", [" third"]],
      ]),
    );
  });

  it.each(["\r\n", "\n"])("preserves folded headers with %j line endings", (eol) => {
    const request = parseHttpRequest(
      ["GET / HTTP/1.1", "X-Test: first", "  continued", "\tlast", "", ""].join(eol),
    );
    expect(request.headers.get("X-Test")).toEqual([` first${eol}  continued${eol}\tlast`]);
  });

  it("does not use body line endings when preserving folded headers", () => {
    const request = parseHttpRequest("POST / HTTP/1.1\nX-Test: first\n second\n\nbody\r\n");
    expect(request.headers.get("X-Test")).toEqual([" first\n second"]);
    expect(request.body).toBe("body\r\n");
  });

  it("preserves empty and whitespace-only header values", () => {
    const request = parseHttpRequest("GET / HTTP/1.1\r\nX-Empty:\r\nX-Space: \t \r\n\r\n");
    expect(request.headers).toEqual(
      new Map([
        ["X-Empty", [""]],
        ["X-Space", [" \t "]],
      ]),
    );
  });

  it.each(["HTTP/1.0", "HTTP/1.1", "HTTP/2.0"])("accepts %s without headers or a body", (version) => {
    expect(parseHttpRequest(`GET / ${version}\r\n\r\n`)).toEqual({
      method: "GET",
      target: "/",
      headers: new Map(),
      body: "",
      httpVersion: version.slice(5),
    });
  });

  it("preserves an absolute request target and an extension method", () => {
    const request = parseHttpRequest("PROPFIND http://example.org/a?q=1 HTTP/1.1\r\n\r\n");
    expect(request.method).toBe("PROPFIND");
    expect(request.target).toBe("http://example.org/a?q=1");
  });

  it("preserves the body after the first separator without trimming or splitting it", () => {
    const body = ' \t{"text":"a:b"}\r\n\r\nlast\n ';
    expect(parseHttpRequest("POST / HTTP/1.1\r\n\r\n" + body).body).toBe(body);
  });

  it("preserves multipart boundaries, part headers and body line endings", () => {
    const body = [
      "--boundary",
      'Content-Disposition: form-data; name="field"',
      "",
      "value",
      "--boundary--",
      "",
    ].join("\r\n");
    const request = parseHttpRequest(
      "POST /upload HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=boundary\r\n\r\n" +
        body,
    );
    expect(request.body).toBe(body);
    expect(request.headers.get("Content-Type")).toEqual([
      " multipart/form-data; boundary=boundary",
    ]);
  });

  it.each(["", " \t\r\n"])("rejects empty input %j", (raw) => {
    expect(() => parseHttpRequest(raw)).toThrow("The raw HTTP request is empty.");
  });

  it("rejects a missing header/body separator", () => {
    expect(() => parseHttpRequest("GET / HTTP/1.1\r\nHost: example.org")).toThrow(
      "The HTTP request is missing the header/body separator.",
    );
  });

  it.each([
    "HTTP/1.1 200 OK",
    "GET / HTTP/3.0",
    "GET /",
    "GET /some path HTTP/1.1",
    "G(ET / HTTP/1.1",
  ])("rejects an invalid request line %j", (line) => {
    expect(() => parseHttpRequest(line + "\r\n\r\n")).toThrow(
      "Expected an HTTP/1.0, HTTP/1.1 or HTTP/2.0 request line.",
    );
  });

  it.each(["MissingColon", ": value", "Bad Name: value", "Name@: value"])(
    "rejects an invalid header %j",
    (header) => {
      expect(() => parseHttpRequest(`GET / HTTP/1.1\r\n${header}\r\n\r\n`)).toThrow(
        "The HTTP request contains an invalid header name.",
      );
    },
  );

  it("rejects a continuation without a preceding header", () => {
    expect(() => parseHttpRequest("GET / HTTP/1.1\r\n folded\r\n\r\n")).toThrow(
      "A folded HTTP header has no preceding header.",
    );
  });
});
