# Tiger Proxy

A tool to give you flexible Routing from a to b and to protocol the traffic seen by the proxy.
For a detailed explanation of the configuration options see the tiger user manual.

## HTTP/2 status

Tiger Proxy does not support HTTP/2 request handling yet. The TLS listener only advertises ALPN
`http/1.1` by default, so HTTP/2 clients should fall back to HTTP/1.1 or fail fast if they insist
on `h2`.

To verify ALPN behavior:

```bash
openssl s_client -connect 127.0.0.1:PORT -alpn "h2,http/1.1" -servername localhost </dev/null 2>/dev/null | rg -n "ALPN|Protocol"
```

Expected: the negotiated protocol is `http/1.1` (or no ALPN selection is reported).

To verify with curl:

```bash
curl -vk --http2 https://127.0.0.1:PORT/
```

Expected: curl uses HTTP/1.1, or fails early if it is forced to require HTTP/2.

## OTLP over HTTP/1.1

Tiger can decode OTLP/HTTP payloads sent over HTTP/1.1 (protobuf, plus JSON if your exporter
emits JSON). OTLP over gRPC/HTTP/2 is not supported yet.

Example (protobuf over HTTP/1.1):

```bash
curl -v -X POST http://127.0.0.1:PORT/v1/traces \
  -H "Content-Type: application/x-protobuf" \
  --data-binary @traces.pb
```

Example (JSON over HTTP/1.1):

```bash
curl -v -X POST http://127.0.0.1:PORT/v1/metrics \
  -H "Content-Type: application/json" \
  --data-binary @metrics.json
```
