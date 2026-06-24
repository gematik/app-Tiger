# tiger-canopy

> **CANOPY** — a programmable DNS server that dynamically reroutes selected hostnames to a Tiger
> proxy. Replaces brittle, static Docker alias setups with runtime-configurable interception.

**For comprehensive user documentation, see [CANOPY User Guide](doc/CANOPY-USER-GUIDE.md).**

## What it does

Inside a containerised test environment, the typical pattern for "I want this hostname's traffic
to go through the Tiger proxy" is to wire up Docker network aliases at `docker compose` time.
That's static, hard to change between scenarios, and forces an environment restart for every
routing tweak.

CANOPY replaces that with a tiny DNS server you point your containers at:

- For hostnames you've **registered** as proxied → CANOPY answers with the Tiger proxy IP.
- For everything else → CANOPY forwards the query to the system upstream (`/etc/resolv.conf` or a
  configured override).

The list of "proxied hosts" is mutable at runtime via a REST API, so test scenarios can swap the
routing on the fly with a single HTTP call (or one Cucumber step — see below).

## Architecture

```
                          ┌────────────────────────────────────┐
   DNS query (UDP/TCP 53) │  CanopyDnsServer                   │
                       ──►│    │                               │
                          │    ▼                               │
                          │  ResolverChain                     │
                          │    ├─ ProxiedHostRegistry hit ────►│  A/AAAA of Tiger proxy
                          │    └─ SystemDnsResolver (dnsjava)  │  forward to /etc/resolv.conf
                          └────────────────────────────────────┘
                                        ▲
                                        │ events
   REST (HTTP 8080) ─► ProxiedHostsController ─► ProxiedHostRegistry
                                        │
                                        └─► RegistryToProxyBridge ─► TigerProxyAdminClient (stage 2)
```

## Quick start

### Docker

```bash
docker run --rm \
  -p 53:53/udp -p 53:53/tcp -p 8080:8080 \
  -e TIGER_PROXY_URL=http://tiger-proxy:9090/ \
  gematik1/tiger-canopy-image:latest
```

A complete docker-compose example is in
[`src/test/resources/docker-compose.example.yaml`](src/test/resources/docker-compose.example.yaml)
and includes three equivalent ways to configure CANOPY (see "Configuration" below).

### From a Tiger testsuite (Cucumber)

CANOPY ships a glue file in `tiger-test-lib` (`CanopyGlue`):

```gherkin
Given TGR set CANOPY base URL to http://canopy:8080

When TGR add the following proxied hosts to CANOPY:
| host             | matchType |
| api.example.com  | EXACT     |
| example.com      | SUFFIX    |
Then TGR CANOPY contains proxied host api.example.com

When TGR remove proxied host api.example.com from CANOPY
```

The base URL can also be supplied once via the `tiger.canopy.baseUrl` configuration key (e.g. in
your testenv YAML).

## Configuration

| Property                        | Env var (canonical)                                                | Default | Description                                                                                                                                                                                                     |
|---------------------------------|--------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `canopy.dnsPort`                | `CANOPY_DNS_PORT`                                                  | `53`    | UDP/TCP port for the DNS listener. Requires `CAP_NET_BIND_SERVICE` for ports < 1024 (the official image takes care of this with `setcap`).                                                                      |
| `canopy.tigerProxyUrl`          | `TIGER_PROXY_URL`                                                  | —       | URL of the Tiger proxy. The host part is resolved on startup and refreshed every 60 s.                                                                                                                          |
| `canopy.proxiedHosts[]`         | `CANOPY_PROXIEDHOSTS_<n>_HOST` / `…_MATCHTYPE` / `…_TIGERPROXYURL` | empty   | Initial registry entries (see env-var caveats below). The optional per-entry `tigerProxyUrl` overrides `canopy.tigerProxyUrl` for that entry only — used to fan a single canopy out to several reverse proxies. |
| `canopy.controlMode`            | `CANOPY_CONTROL_MODE`                                              | `NONE`  | Control mode selector — `NONE`, or `ROUTE_PER_HOST` (calls the Tiger-proxy admin API on every registry change).                                                                                                 |
| `canopy.upstreamDnsServers[]`   | `CANOPY_UPSTREAMDNSSERVERS_<n>`                                    | empty   | Optional override of the upstream resolver list. If empty, `/etc/resolv.conf` is consulted.                                                                                                                     |
| `canopy.defaultTtlSeconds`      | `CANOPY_DEFAULTTTLSECONDS`                                         | `30`    | TTL on synthesized A records pointing to the Tiger proxy.                                                                                                                                                       |
| `canopy.proxyClientHttpVersion` | `CANOPY_PROXYCLIENTHTTPVERSION`                                    | `AUTO`  | HTTP version used by the stage-2 admin client (`AUTO`/`HTTP_1_1`/`HTTP_2`). `AUTO` picks HTTP/2 for `https://` and HTTP/1.1 for `http://`.                                                                      |
| `server.port`                   | `SERVER_PORT`                                                      | `8080`  | REST API port (Spring Boot standard).                                                                                                                                                                           |

### Three equivalent ways to set configuration

OS env vars are a flat string namespace, so for nested config you have a choice:

**1. `SPRING_APPLICATION_JSON`** — one env var with the full nested tree (parsed natively):

```yaml
SPRING_APPLICATION_JSON: |
  {
    "canopy": {
      "dnsPort": 53,
      "tigerProxyUrl": "http://tiger-proxy:9090/",
      "proxiedHosts": [
        {"host": "api.example.com", "matchType": "EXACT"},
        {"host": "example.com",     "matchType": "SUFFIX"}
      ]
    }
  }
```

**2. Indexed env vars** — one var per leaf field; verbose but explicit:

```yaml
CANOPY_DNS_PORT: "53"
TIGER_PROXY_URL: "http://tiger-proxy:9090/"
CANOPY_PROXIEDHOSTS_0_HOST: "api.example.com"
CANOPY_PROXIEDHOSTS_0_MATCHTYPE: "EXACT"
```

**3. Mounted `application.yaml`** — best for non-trivial setups:

```yaml
volumes:
  - ./canopy-overrides.yaml:/app/config/application.yaml:ro
environment:
  SPRING_CONFIG_ADDITIONAL_LOCATION: "/app/config/"
```

## REST API

Base path: `/api/v1/proxied-hosts`.

| Method   | Path                | Body                           | Response                                      |
|----------|---------------------|--------------------------------|-----------------------------------------------|
| `GET`    | `/`                 | —                              | `200` `[ProxiedHostDto]`                      |
| `POST`   | `/`                 | `{host, matchType?}`           | `201` `ProxiedHostDto` (idempotent)           |
| `POST`   | `/bulk`             | `{hosts:[{host, matchType?}]}` | `200` `{added, unchanged}`                    |
| `DELETE` | `/{host}`           | —                              | `204`                                         |
| `DELETE` | `/`                 | —                              | `204` (clears all)                            |
| `GET`    | `/config`           | —                              | `200` `{tigerProxyUrl, controlMode, dnsPort}` |
| `PUT`    | `/config/proxy-url` | `{url}`                        | `200` `ConfigDto` (re-resolves immediately)   |
| `GET`    | `/actuator/health`  | —                              | `200` (incl. DNS socket bind check)           |

### Examples

```bash
# Add an exact-match host
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
     -H 'Content-Type: application/json' \
     -d '{"host":"api.example.com","matchType":"EXACT"}'

# Bulk add
curl -X POST http://canopy:8080/api/v1/proxied-hosts/bulk \
     -H 'Content-Type: application/json' \
     -d '{"hosts":[
           {"host":"api.example.com","matchType":"EXACT"},
           {"host":"example.com",    "matchType":"SUFFIX"}
         ]}'

# Per-host tigerProxyUrl override — routes this entry to a different Tiger proxy than
# canopy.tigerProxyUrl. Useful when a single canopy fans DNS out to several reverse proxies
# (e.g. one HTTP proxy + one POP3 proxy + one SMTP proxy).
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
     -H 'Content-Type: application/json' \
     -d '{"host":"pop3.example.com","tigerProxyUrl":"http://pop3-tp:9100"}'

# List
curl http://canopy:8080/api/v1/proxied-hosts

# Remove
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts/api.example.com

# Repoint at a different Tiger proxy at runtime
curl -X PUT http://canopy:8080/api/v1/proxied-hosts/config/proxy-url \
     -H 'Content-Type: application/json' \
     -d '{"url":"http://tiger-proxy-2:9090/"}'
```

Match types:

- `EXACT` — must equal the queried name (after lowercase + trailing-dot strip).
- `SUFFIX` — matches the queried name and any sub-domain (longest match wins).

## Control Modes: DNS-Only vs Dynamic-Route

| Mode              | `controlMode`    | What CANOPY does                                                                                                                                                                            |
|-------------------|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **DNS-Only**      | `NONE` (default) | DNS rewriting only. The Tiger proxy must be configured separately (statically).                                                                                                             |
| **Dynamic-Route** | `ROUTE_PER_HOST` | Every registry add triggers a `PUT /route` on the Tiger proxy admin API; every removal triggers a `DELETE /route/{id}`. The route id returned by the proxy is stored on the registry entry. |

In Dynamic-Route mode, the proxy URL is read from `canopy.tigerProxyUrl` on every call so runtime updates
through `PUT /config/proxy-url` are honored immediately.

## Port 53 caveat

Linux requires `CAP_NET_BIND_SERVICE` to bind ports < 1024. The official image:

- runs as a non-root user (UID 10000), and
- applies `setcap 'cap_net_bind_service=+ep' $JAVA_HOME/bin/java` so the JVM inherits the capability.

If you build your own image without that, you have three options:

1. Run the container as root (`--user 0`).
2. Pass `--cap-add=NET_BIND_SERVICE` and use the official setcap'd image.
3. Configure CANOPY to listen on an unprivileged port (e.g. `canopy.dnsPort=1053`) and let Docker
   map host port 53 to it.

## Building and running locally

```bash
# Build the jar
mvn -pl tiger-canopy -am package

# Run the jar directly (port 53 needs root or CAP_NET_BIND_SERVICE)
java -jar tiger-canopy/target/tiger-canopy-*-exec.jar \
     --canopy.dnsPort=1053 \
     --canopy.tigerProxyUrl=http://localhost:9090/

# Or build the image and run via docker compose
mvn -pl tiger-canopy -am package -DskipTests
docker build -t gematik1/tiger-canopy-image:dev tiger-canopy
docker compose -f tiger-canopy/src/test/resources/docker-compose.example.yaml up
```

## Tests

```bash
mvn -pl tiger-canopy verify
```

Failsafe runs two `*IT.java` tests:

- `CanopyProxyBridgeIT` — full Spring context + WireMock; verifies stage-2 `PUT /route` /
  `DELETE /route/{id}` calls.
- `CanopyDockerComposeIT` — builds the image from this module's `Dockerfile`, boots the
  container, and asserts that a registered host resolves to the configured proxy IP via the
  running DNS server. Auto-skipped when no Docker daemon is available.

## Further reading

- [ADR / implementation plan](../doc/adr/canopy-implementation-plan.md)
- [docker-compose example](src/test/resources/docker-compose.example.yaml)
