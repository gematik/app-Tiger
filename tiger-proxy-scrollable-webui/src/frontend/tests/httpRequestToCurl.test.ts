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
import { parseHttpRequest, type HttpRequest } from "../src/components/httpRequestParser";
import { convertToCurl } from "../src/components/httpRequestToCurl";

function request(overrides: Partial<HttpRequest> = {}): HttpRequest {
  return {
    method: "GET",
    target: "/",
    headers: new Map([["Host", [" example.org"]]]),
    body: "",
    ...overrides,
  };
}

describe("convertToCurl", () => {
  it.each([
    ["bash", "http"],
    ["bash", "https"],
    ["cmd", "http"],
    ["cmd", "https"],
  ] as const)("replays a recorded HTTP/2 request for %s over %s", (shell, protocol) => {
    const body = '{"text":"hello"}\r\n';
    const parsed = parseHttpRequest(
      "POST /api?q=hello%20world HTTP/2.0\r\n" +
      "host: example.org:8443\r\n" +
      "content-type: application/json\r\n" +
      "x-test: first\r\nx-test: second\r\n" +
      "content-length: 18\r\n\r\n" + body,
    );
    const command = convertToCurl(parsed, shell, protocol);
    expect(command).toContain(" --http2-prior-knowledge ");
    expect(command).toContain(`${protocol}://example.org:8443/api?q=hello`);
    expect(command).not.toContain("content-length");
    expect(parsed.body).toBe(body);
    const expected = convertToCurl({ ...parsed, httpVersion: "1.1" }, shell, protocol);
    expect(command.replace(" --http2-prior-knowledge", "")).toBe(expected);
  });

  it.each(["1.0", "1.1"] as const)("does not add HTTP/2 options for HTTP/%s", (version) => {
    const parsed = parseHttpRequest(`GET / HTTP/${version}\r\nHost: example.org\r\n\r\n`);
    expect(convertToCurl(parsed)).not.toContain("--http2");
  });

  it.each(["bash", "cmd"] as const)(
    "converts the recorded percent-encoded GET request for %s",
    (shell) => {
      const target =
        "/get?my_cool_param=client_id&state=some+weird+%24value%C2%A7" +
        "&redirect_uri=https%3A%2F%2Fmy.redirect";
      const rawRequest = [
        `GET ${target} HTTP/1.1`,
        "Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1",
        "Accept: */*",
        "key: value",
        "Proxy-Connection: Keep-Alive",
        "User-Agent: Apache-HttpClient/4.5.14 (Java/17.0.20.1)",
        "Accept-Encoding: gzip,deflate",
        "content-length: 0",
        "Host: localhost:61387",
        "",
        "",
      ].join("\r\n");
      const expectedBash =
        "curl -v --globoff --path-as-is -X 'GET'" +
        ` --url 'http://localhost:61387${target}'` +
        " -H 'Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1'" +
        " -H 'Accept: */*' -H 'key: value' -H 'Proxy-Connection: Keep-Alive'" +
        " -H 'User-Agent: Apache-HttpClient/4.5.14 (Java/17.0.20.1)'" +
        " -H 'Accept-Encoding: gzip,deflate' -H 'Host: localhost:61387'";
      const expectedCmd =
        'curl -v --globoff --path-as-is -X ^"GET^"' +
        ' --url ^"http://localhost:61387/get?my_cool_param=client_id' +
        '^&state=some+weird+^%^24value^%^C2^%^A7^&redirect_uri=https^%^3A^%^2F^%^2Fmy.redirect^"' +
        ' -H ^"Content-Type: application/x-www-form-urlencoded; charset=ISO-8859-1^"' +
        ' -H ^"Accept: */*^" -H ^"key: value^" -H ^"Proxy-Connection: Keep-Alive^"' +
        ' -H ^"User-Agent: Apache-HttpClient/4.5.14 ^(Java/17.0.20.1^)^"' +
        ' -H ^"Accept-Encoding: gzip,deflate^" -H ^"Host: localhost:61387^"';

      expect(convertToCurl(parseHttpRequest(rawRequest), shell)).toBe(
        shell === "bash" ? expectedBash : expectedCmd,
      );
    },
  );

  it("defaults to Bash and constructs an HTTP URL", () => {
    expect(convertToCurl(request())).toBe(
      "curl -v --globoff --path-as-is -X 'GET' --url 'http://example.org/' -H 'Host: example.org'",
    );
  });

  it("uses curl with caret-quoted arguments for cmd", () => {
    expect(convertToCurl(request(), "cmd")).toBe(
      'curl -v --globoff --path-as-is -X ^"GET^" --url ^"http://example.org/^" -H ^"Host: example.org^"',
    );
  });

  it("uses an absolute HTTP target without requiring Host", () => {
    expect(
      convertToCurl(request({ target: "http://other.example:8080/a", headers: new Map() })),
    ).toBe("curl -v --globoff --path-as-is -X 'GET' --url 'http://other.example:8080/a'");
  });

  it.each(["bash", "cmd"] as const)("uses HTTPS metadata for a relative target in %s", (shell) => {
    const command = convertToCurl(request({ target: "/path?q=value" }), shell, "https");
    expect(command).toContain("https://example.org/path?q=value");
  });

  it.each(["http", "https"] as const)(
    "preserves an absolute %s URL regardless of transport metadata",
    (protocol) => {
      const target = `${protocol}://other.example:8443/path`;
      expect(
        convertToCurl(request({ target, headers: new Map() }), "bash", protocol === "http" ? "https" : "http"),
      ).toContain(`--url '${target}'`);
    },
  );

  it("finds Host case-insensitively and preserves the path and query", () => {
    const command = convertToCurl(
      request({
        target: "/a/../b?q=hello%20world&items=[1]",
        headers: new Map([["hOsT", [" \texample.org:8080 "]]]),
      }),
    );
    expect(command).toContain("--url 'http://example.org:8080/a/../b?q=hello%20world&items=[1]'");
    expect(command).toContain("-H 'hOsT: \texample.org:8080 '");
  });

  it("emits separate arguments for repeated headers and preserves spelling and whitespace", () => {
    expect(
      convertToCurl(
        request({
          target: "http://example.org/",
          headers: new Map([
            ["X-Test", [" first", "\tsecond  "]],
            ["x-test", ["third"]],
          ]),
        }),
      ),
    ).toBe(
      "curl -v --globoff --path-as-is -X 'GET' --url 'http://example.org/'" +
        " -H 'X-Test: first' -H 'X-Test:\tsecond  ' -H 'x-test:third'",
    );
  });

  it("omits Content-Length regardless of casing", () => {
    const command = convertToCurl(
      request({
        headers: new Map([
          ["Host", [" example.org"]],
          ["CONTENT-LENGTH", [" 999"]],
          ["content-length", [" 888"]],
        ]),
      }),
    );
    expect(command).not.toMatch(/content-length/i);
  });

  it("uses curl's semicolon syntax to send an empty header", () => {
    expect(
      convertToCurl(
        request({
          headers: new Map([
            ["Host", [" example.org"]],
            ["X-Empty", ["", " \t"]],
          ]),
        }),
      ),
    ).toContain("-H 'X-Empty;' -H 'X-Empty;'");
  });

  it("omits data options for an empty body", () => {
    expect(convertToCurl(request())).not.toContain("--data");
    expect(convertToCurl(request())).not.toContain("Content-Type");
  });

  it("sends a literal body starting with @ and suppresses curl's default Content-Type", () => {
    expect(convertToCurl(request({ method: "POST", body: "@not-a-file" }))).toContain(
      "--data-raw '@not-a-file' -H 'Content-Type:'",
    );
  });

  it("preserves an existing Content-Type without adding another", () => {
    const command = convertToCurl(
      request({
        method: "POST",
        headers: new Map([
          ["Host", [" example.org"]],
          ["cOnTeNt-TyPe", [" application/json"]],
        ]),
        body: '{"value":1}',
      }),
    );
    expect(command).toContain("-H 'cOnTeNt-TyPe: application/json'");
    expect(command).toContain(`--data-raw '{"value":1}'`);
    expect(command).not.toContain("-H 'Content-Type:'");
  });

  it("quotes Bash apostrophes without expanding dollar signs or backticks", () => {
    const command = convertToCurl(request({ body: "it's $HOME `whoami`" }), "bash");
    expect(command).toContain(String.raw`--data-raw 'it'\''s $HOME ` + "`whoami`'");
  });

  it("preserves multiline multipart text in Bash", () => {
    const body =
      '--boundary\r\nContent-Disposition: form-data; name="field"\r\n\r\nvalue\r\n--boundary--\r\n';
    const command = convertToCurl(
      request({
        method: "POST",
        headers: new Map([
          ["Host", [" example.org"]],
          ["Content-Type", [" multipart/form-data; boundary=boundary"]],
        ]),
        body,
      }),
      "bash",
    );
    expect(command).toContain(`--data-raw '${body}'`);
    expect(command).toContain("-H 'Content-Type: multipart/form-data; boundary=boundary'");
  });

  it("escapes cmd operators in the URL", () => {
    expect(convertToCurl(request({ target: "/?a=1&b=(2)|<3>^" }), "cmd")).toContain(
      '--url ^"http://example.org/?a=1^&b=^(2^)^|^<3^>^^^"',
    );
  });

  it("escapes JSON quotes for cmd", () => {
    expect(convertToCurl(request({ body: '{"key":"value"}' }), "cmd")).toContain(
      String.raw`--data-raw ^"{\^"key\^":\^"value\^"}^"`,
    );
  });

  it("escapes exclamation marks and environment variable expressions for cmd", () => {
    expect(convertToCurl(request({ body: "Hello! %PATH%" }), "cmd")).toContain(
      '--data-raw ^"Hello^! ^%^PATH^%^"',
    );
  });

  it.each(["\r\n", "\n", "\r"])(
    "uses Chrome-style doubled newlines for cmd body line ending %j",
    (lineEnding) => {
      expect(
        convertToCurl(request({ body: ["first", "", "last"].join(lineEnding) }), "cmd"),
      ).toContain('--data-raw ^"first^\n\n^\n\nlast^"');
    },
  );

  it("doubles trailing backslashes for Windows argument parsing", () => {
    expect(
      convertToCurl(request({ body: String.raw`C:\folder` + String.fromCodePoint(92) }), "cmd"),
    ).toContain(String.raw`--data-raw ^"C:\folder\\^"`);
  });

  it.each(["bash", "cmd"] as const)("rejects NUL characters for %s", (shell) => {
    expect(() =>
      convertToCurl(request({ body: "a" + String.fromCodePoint(0) + "b" }), shell),
    ).toThrow("Shell arguments cannot contain a NUL character.");
  });

  it.each(["/relative", "*", "ftp://example.org/"])(
    "rejects targets that cannot produce an HTTP URL: %s",
    (target) => {
      expect(() => convertToCurl(request({ target, headers: new Map() }))).toThrow(
        "An HTTP URL or a request path with a Host header is required.",
      );
    },
  );

  it("rejects CONNECT requests", () => {
    expect(() => convertToCurl(request({ method: "CONNECT" }))).toThrow(
      "CONNECT requests are not supported.",
    );
  });

  it.each([
    ["Content-Encoding", " gzip"],
    ["TRANSFER-ENCODING", " chunked"],
  ])("rejects unsupported %s: %s", (name, value) => {
    expect(() => convertToCurl(request({ headers: new Map([[name, [value]]]) }))).toThrow(
      "Compressed or transfer-encoded bodies are not supported yet.",
    );
  });

  it("accepts identity encoding case-insensitively", () => {
    expect(
      convertToCurl(
        request({
          headers: new Map([
            ["Host", [" example.org"]],
            ["Content-Encoding", [" IDENTITY "]],
          ]),
        }),
      ),
    ).toContain("-H 'Content-Encoding: IDENTITY '");
  });

  it("accepts the cmd length limit and rejects commands exceeding it", () => {
    const emptyBodyLength = convertToCurl(request({ body: "x" }), "cmd").length - 1;
    expect(
      convertToCurl(request({ body: "x".repeat(8191 - emptyBodyLength) }), "cmd"),
    ).toHaveLength(8191);
    expect(() =>
      convertToCurl(request({ body: "x".repeat(8192 - emptyBodyLength) }), "cmd"),
    ).toThrow("This request is too large for CMD. Try Bash instead.");
  });

  it("does not apply the cmd length limit to Bash", () => {
    expect(convertToCurl(request({ body: "x".repeat(9000) }), "bash").length).toBeGreaterThan(8191);
  });

  it("does not modify the input request or its header values", () => {
    const original = request({
      headers: new Map([
        ["Host", [" example.org "]],
        ["Content-Length", [" 99"]],
      ]),
      body: " unchanged ",
    });
    const before = structuredClone(original);
    convertToCurl(original);
    expect(original).toEqual(before);
  });
});
