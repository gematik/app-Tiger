# CANOPY User Guide

**Version:** 1.0
**Last Updated:** June 3, 2026
**Audience:** Test engineers, QA automation specialists, DevOps professionals

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start: Choose Your Path](#quick-start-choose-your-path)
3. [Detailed Configuration](#detailed-configuration)
4. [Usage Patterns](#usage-patterns)
5. [DNS-Only vs Dynamic-Route Modes](#dns-only-vs-dynamic-route-modes)
6. [Cucumber Integration](#cucumber-integration)
7. [REST API Reference](#rest-api-reference)
8. [Troubleshooting](#troubleshooting)
9. [Complete End-to-End Examples](#complete-end-to-end-examples)
10. [Architecture & Design](#architecture--design)
11. [Further Reading](#further-reading)
12. [Checklists](#checklists)

---

## Overview

**What is CANOPY?**

CANOPY is a programmable DNS server that dynamically reroutes selected hostnames to a Tiger proxy. It replaces brittle
static Docker network configurations with a runtime-configurable routing system.

**The Problem It Solves**

In traditional containerized test environments, you wire up Docker network aliases at `docker compose` time to intercept
traffic:

- Static: Hard-coded at container start, can't change without restart
- Fragile: Breaking aliases requires environment rebuilding
- Inflexible: Every routing change needs infrastructure restart
- Hard to debug: Network configuration spread across multiple files

**CANOPY's Solution**

Instead of static Docker aliases, CANOPY runs a DNS server that:

- Answers DNS queries with the Tiger proxy address for **registered hosts**
- Forwards everything else to the system upstream resolver
- Allows runtime host registration and removal via **REST API or Cucumber steps**
- Optionally auto-configures the Tiger proxy (Stage 2 mode)
- Enables **test-driven routing**: scenarios define what gets intercepted

**High-Level Architecture**

```
┌─────────────────────────────────────────┐
│      Container requesting DNS           │
│      (e.g., curl http://api.example)    │
└──────────────┬──────────────────────────┘
               │ DNS query
               ▼
┌──────────────────────────────────────────┐
│         CANOPY DNS Server                │
│  ┌──────────────────────────────────┐   │
│  │  Registered hosts?               │   │
│  │  → Return Tiger proxy IP         │   │
│  │  → Forward to system DNS         │   │
│  └──────────────────────────────────┘   │
└──────────────┬───────────────────────────┘
               │ Resolved IP (proxy or real)
               ▼
┌──────────────────────────────────────────┐
│      Container connects to IP            │
│      (transparent interception)          │
└──────────────────────────────────────────┘
```

**When to Use CANOPY**

✅ **Good fit:**

- Multi-scenario test suites where routing changes per scenario
- Chaos testing / hypothesis testing (disable routes to test fallbacks)
- API testing with selective interception
- Development environments with changing backend URLs
- Integration tests where you need to swap endpoints dynamically

❌ **Not a good fit:**

- Simple single-scenario docker-compose setups (use network aliases)
- Pure performance testing (adds DNS hop)
- Environments where config must be 100% static (compliance)

---

## Quick Start: Choose Your Path

### Path A: Pure Docker

**Goal:** Run CANOPY standalone with docker-compose; no Tiger integration.

**Prerequisites:**

- Docker & docker-compose installed
- Network connectivity (to pull image)

**Steps:**

1. **Create docker-compose.yaml:**

```yaml
version: '2.4'

services:
  tiger-proxy:
      image: gematik1/tiger-proxy-image:latest
      container_name: tiger-proxy
      ports:
         - "9000:9000"   # Admin UI
         - "9090:9090"   # Proxy port
      networks:
         - test-network
      environment:
         TIGER_PROXY_PORT: 9090
         TIGER_PROXY_ADMIN_PORT: 9000

  canopy:
    image: gematik1/tiger-canopy-image:latest
    container_name: canopy
    ports:
      - "53:53/udp"
      - "53:53/tcp"
      - "8080:8080"
    environment:
       # All CANOPY configuration in one place via SPRING_APPLICATION_JSON —
       # Spring Boot parses this JSON blob natively into the environment,
       # preserving full nested structure. Alternatives (indexed env vars,
       # mounted YAML file) are documented in the file header.
      SPRING_APPLICATION_JSON: |
         {
           "canopy": {
             "dnsPort": 53,
             "tigerProxyUrl": "http://tiger-proxy:9090/",
             "controlMode": "NONE",
             "proxiedHosts": [
               {"host": "api.example.com", "matchType": "EXACT"},
               {"host": "example.com",     "matchType": "SUFFIX"}
             ]
           }
         }
    networks:
      - test-net

  # Example: resolv-writer sidecar that writes a shared resolv.conf
  # Clients mount ./dns/resolv.conf as /etc/resolv.conf:ro and depend on
  # the writer becoming healthy. This is useful when client images lack
  # getent/awk/sed or when you want a single source-of-truth for DNS.

  resolv-writer:
    image: alpine:3
    # Resolve canopy, write resolv.conf with CANOPY first and Docker stub as fallback,
    # then stay alive so we can expose a healthcheck. Adjust retries/timeouts as needed.
    command: ["sh", "-c", "for i in $(seq 1 30); do IP=$(getent hosts canopy | awk '{print $1}') && [ -n \"$IP\" ] && break; sleep 1; done; \
      if [ -z \"$IP\" ]; then echo 'ERROR: could not resolve canopy' >&2; exit 1; fi; \
      cat > /dns/resolv.conf <<EOF\n+nameserver $IP\n+nameserver 127.0.0.11\n+EOF\n+      # Keep container running so healthcheck remains valid
      tail -f /dev/null"]
    networks:
      - testhub_network
    depends_on:
      - canopy
    volumes:
      - ./dns:/dns
    healthcheck:
      test: ["CMD-SHELL","[ -s /dns/resolv.conf ] && grep -q nameserver /dns/resolv.conf || exit 1"]
      interval: 5s
      timeout: 2s
      retries: 5

  test-app:
    image: my-test-app:latest
    networks:
      - testhub_network
    depends_on:
      resolv-writer:
        condition: service_healthy
    volumes:
      - ./dns/resolv.conf:/etc/resolv.conf:ro
    command: ["sh","-c","exec \"$@\""]

networks:
   testhub_network:
      driver: bridge
```

2. **Start the environment:**

```bash
docker compose up -d
```

3. **Verify DNS resolution:**

```bash
# The resolv-writer produced ./dns/resolv.conf which the client mounted as /etc/resolv.conf
docker compose exec test-app nslookup api.example.com
# Should return: Canopy's IP (the proxy IP)
```

4. **Add a host at runtime (REST API):**

```bash
curl -X POST http://localhost:8080/api/v1/proxied-hosts \
     -H 'Content-Type: application/json' \
     -d '{"host":"login.example.com","matchType":"EXACT"}'
```

5. **Verify it works:**

```bash
docker compose exec test-app curl -v http://api.example.com
# Should be intercepted by Tiger proxy (check logs)
```

**Troubleshooting:**

- DNS not resolving? Check `docker compose logs canopy` for startup errors
- Port 53 already in use? Switch to ephemeral port: `dnsPort: 5353` in environment vars and update the resolv-writer
  command
  to use the correct port

---

### Path B: Tiger Integration

**Goal:** Use CANOPY within Tiger testenv-mgr with auto-wiring and Docker DNS injection.

**Prerequisites:**

- Tiger project set up (pom.xml, tiger.yaml)
- `tiger-canopy-extension` in POM dependencies
- Tiger test runner available

**Dependency Setup (pom.xml):**

```xml

<dependency>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-canopy-extension</artifactId>
    <version>${tiger.version}</version>
</dependency>
```

**Steps:**

1. **Create tiger.yaml:**

```yaml
localProxyActive: true

servers:
  # Tiger proxy (required for canopy to route through)
  myProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090

  # CANOPY DNS server
  myCanopy:
    type: canopy
    canopy:
      # tigerProxyUrl auto-wired to ${myProxy.adminUrl}
      # controlMode auto-set to ROUTE_PER_HOST
      proxiedHosts:
        - host: api.example.com
          matchType: EXACT
        - host: example.org
          matchType: SUFFIX

  # Test application
  testApp:
    type: docker
    docker:
      image: my-test-app:latest
      exposedPorts: [ 8080 ]
    # DNS is auto-injected; testApp uses Canopy
```

2. **Run your Tiger test suite:**

```bash
mvn verify
```

Tiger will:

- Start the proxy
- Start CANOPY with auto-wired proxy URL
- Start test app with DNS set to CANOPY
- Routes are automatically registered on the proxy

3. **Verify in Cucumber steps:**

```gherkin
Given TGR set CANOPY base URL to http://myCanopy:8080

When TGR add the following proxied hosts to CANOPY:
| host              | matchType |
| service.example   | EXACT     |
| *.internal        | SUFFIX    |

Then TGR CANOPY contains proxied host service.example

# Later, swap routing:
When TGR remove proxied host service.example from CANOPY
```

**What Happens Automatically:**

- ✅ Proxy starts first (dependency ordering)
- ✅ Canopy reads proxy URL from Tiger's placeholder system
- ✅ Canopy registers routes with proxy (Stage 2)
- ✅ Test containers get Canopy's IP as DNS server
- ✅ Placeholder resolution: `${myCanopy.baseUrl}`, `${myCanopy.dnsAddress}`, etc.

**Troubleshooting:**

- Auto-wiring didn't happen? Check you have exactly **one** tigerProxy sibling
- DNS not injected? Verify `docker.injectDns: true` (default) in test app config

---

### Path C: Existing Docker Compose → Tiger Integration Migration

**Goal:** Convert working docker-compose setup to Tiger testenv-mgr.

**Before (docker-compose.yaml - static):**

```yaml
services:
  canopy:
    image: gematik1/tiger-canopy-image:latest
    environment:
      CANOPY_PROXIEDHOSTS_0_HOST: "api.service"
      CANOPY_PROXIEDHOSTS_0_MATCHTYPE: "EXACT"
    networks:
      - test-net

  myapp:
    image: my-test-app:latest
    depends_on:
      - canopy
    networks:
      - test-net

networks:
  test-net:
    driver: bridge
```

**After (tiger.yaml - dynamic):**

```yaml
servers:
  myCanopy:
    type: canopy
    canopy:
      proxiedHosts:
        - host: api.service
          matchType: EXACT

  testApp:
    type: docker
    docker:
      image: my-app:latest
    # DNS auto-injected; no manual dns: setting needed
```

**Migration Checklist:**

- [ ] Rename config file to `tiger.yaml`
- [ ] Convert service definitions to Tiger server types
- [ ] Remove docker compose DNS configuration (Tiger handles it)
- [ ] Add `type: canopy` for CANOPY service
- [ ] Run Tiger setup: `mvn verify`
- [ ] Verify routes still work by running tests
- [ ] Add Cucumber steps for dynamic routing (optional)

**Key Differences:**

- Docker-compose: Static config, restart needed for changes
- Tiger: Dynamic config, routes can change mid-test
- Docker-compose: Manual DNS setup
- Tiger: Automatic DNS injection

---

## Detailed Configuration

### Tiger YAML Configuration

**Basic Structure:**

```yaml
servers:
  myCanopy:
    type: canopy                      # Required: identifies this as canopy server
    canopy:
      # All fields optional except type
      tigerProxyUrl: http://proxy:9090/  # Tiger proxy URL (or auto-wired)
      controlMode: ROUTE_PER_HOST         # NONE (default) | ROUTE_PER_HOST
      dnsPort: 53                         # Container DNS port (default 53)
      proxiedHosts: [ ]                    # Initial hosts to register
      upstreamDnsServers: [ ]              # Upstream for non-proxied queries
      defaultTtlSeconds: 30               # DNS TTL (default 30)
      proxyClientHttpVersion: AUTO        # HTTP version: AUTO | HTTP_1_1 | HTTP_2

      # Docker-specific (how Tiger launches the container)
      image: gematik1/tiger-canopy-image:latest  # Image to use
      adminPort: 0                        # Admin REST port (0 = ephemeral)
      dnsHostPort: 0                      # DNS host port (0 = ephemeral)
      networks: [ ]                        # Additional Docker networks
      extraEnv: # Extra env vars passed to container
        DEBUG: "true"
```

**Field Explanations:**

| Field                          | Type   | Default      | Purpose                                                       |
|--------------------------------|--------|--------------|---------------------------------------------------------------|
| `tigerProxyUrl`                | URL    | (auto-wired) | Tiger proxy to route through; can be `${<serverId>.adminUrl}` |
| `controlMode`                  | enum   | NONE         | NONE = DNS only; ROUTE_PER_HOST = also config proxy           |
| `dnsPort`                      | int    | 53           | Container port for DNS (UDP+TCP)                              |
| `proxiedHosts[]`               | list   | empty        | Initial hosts registered at startup                           |
| `proxiedHosts[].host`          | string | required     | Hostname to intercept (e.g., api.example.com)                 |
| `proxiedHosts[].matchType`     | enum   | EXACT        | EXACT = exact match; SUFFIX = wildcard                        |
| `proxiedHosts[].tigerProxyUrl` | URL    | inherited    | Override proxy for this host only                             |
| `upstreamDnsServers[]`         | list   | []           | Upstream DNS servers (empty = use system)                     |
| `defaultTtlSeconds`            | int    | 30           | TTL for synthesized answers                                   |
| `proxyClientHttpVersion`       | enum   | AUTO         | HTTP or HTTP/2 for admin API calls                            |
| `adminPort`                    | int    | 0            | REST API port (0 = auto-assign)                               |
| `dnsHostPort`                  | int    | 0            | DNS host port (0 = auto-assign)                               |
| `image`                        | string | latest       | Docker image (tag resolved from pom)                          |
| `networks[]`                   | list   | []           | Additional Docker networks to join                            |
| `extraEnv`                     | map    | {}           | Extra environment variables                                   |

### Three Configuration Methods

**Method 1: Docker Environment Variables (Indexed)**

```bash
export TIGER_PROXY_URL="http://proxy:9090/"
export CANOPY_DNS_PORT="53"
export CANOPY_PROXIEDHOSTS_0_HOST="api.example.com"
export CANOPY_PROXIEDHOSTS_0_MATCHTYPE="EXACT"
```

**Method 2: SPRING_APPLICATION_JSON (Single Env Var)**

```bash
export SPRING_APPLICATION_JSON='{
  "canopy": {
    "dnsPort": 53,
    "tigerProxyUrl": "http://proxy:9090/",
    "proxiedHosts": [
      {"host": "api.example.com", "matchType": "EXACT"}
    ]
  }
}'
```

**Method 3: Mounted YAML File**

```yaml
# canopy-config.yaml
canopy:
  dnsPort: 53
  tigerProxyUrl: http://proxy:9090/
  proxiedHosts:
    - host: api.example.com
      matchType: EXACT
```

```bash
docker run \
  -v ./canopy-config.yaml:/app/config/application.yaml:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION="/app/config/" \
  gematik1/tiger-canopy-image:latest
```

### Auto-Wiring Behavior

When using Tiger testenv-mgr, CANOPY's `tigerProxyUrl` can auto-wire:

**Scenario 1: Single Proxy (Auto-Wire)**

```yaml
servers:
  proxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
  canopy:
    type: canopy
    canopy:
    # tigerProxyUrl left blank → auto-wires to ${proxy.adminUrl}
    # controlMode auto-set to ROUTE_PER_HOST (unless explicitly NONE)
```

**Scenario 2: Multiple Proxies (Manual)**

```yaml
servers:
  httpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
  smtpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9100
  canopy:
    type: canopy
    canopy:
      # Multiple proxies → must specify explicitly
      tigerProxyUrl: ${httpProxy.adminUrl}
      proxiedHosts:
        - host: api.example.com  # → httpProxy
        - host: smtp.example.com
          tigerProxyUrl: ${smtpProxy.adminUrl}  # → smtpProxy
```

### Published Placeholders

After CANOPY starts, these placeholders are available for other servers:

```yaml
${myCanopy.baseUrl}      # http://canopy:8080 (REST API)
${myCanopy.dnsAddress}   # 172.20.0.5 (container network IP)
${myCanopy.dnsPort}      # 53 (DNS host port)
${myCanopy.containerName}  # container_id_hash
```

**Usage Example:**

```yaml
servers:
  myCanopy:
    type: canopy
    # ...

  testApp:
    type: externalJar
    externalJarOptions:
      environment:
        CANOPY_URL: ${myCanopy.baseUrl}
        CANOPY_DNS: ${myCanopy.dnsAddress}:${myCanopy.dnsPort}
```

---

## Usage Patterns

### Pattern 1: Static Configuration

**Use Case:** Hosts are known at startup; routing doesn't change.

**Configuration:**

```yaml
servers:
  canopy:
    type: canopy
    canopy:
      proxiedHosts:
        - host: api.internal.example.com
        - host: db.internal.example.com
        - host: cache.internal.example.com
```

**Behavior:**

- Hosts registered at CANOPY startup
- Remain registered for entire test suite
- Queries for unregistered hosts go to upstream DNS
- No REST API calls needed

**Best For:** Single-scenario suites, integration tests with fixed endpoints

---

### Pattern 2: Runtime Configuration

**Use Case:** Different tests need different routing; routes change mid-suite.

**YAML Setup:**

```yaml
servers:
  canopy:
    type: canopy
    canopy:
      proxiedHosts: [ ]  # Start empty; tests add as needed
```

**Cucumber Steps:**

```gherkin
Feature: Multi-scenario API testing

  Scenario: Test against staging backend
    Given TGR set CANOPY base URL to http://canopy:8080
    When TGR add the following proxied hosts to CANOPY:
      | host        | matchType |
      | api.example | EXACT     |
    Then test calls http://api.example (intercepted)
    When TGR remove proxied host api.example from CANOPY

  Scenario: Test against fallback backend
    # Different hosts registered, different test path
    When TGR add the following proxied hosts to CANOPY:
      | host             | matchType |
      | fallback.example | EXACT     |
    Then test calls http://fallback.example (intercepted)
```

**REST API Calls:**

```bash
# Add hosts
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
  -d '{"host":"api.prod","matchType":"EXACT"}'

# List current hosts
curl http://canopy:8080/api/v1/proxied-hosts

# Remove specific host
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts/api.prod

# Clear all (cleanup)
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts
```

**Best For:** Multi-scenario suites, chaos testing, hypothesis testing

---

### Pattern 3: Multi-Proxy Fan-Out

**Use Case:** Route different protocols to different proxies (HTTP, POP3, SMTP, etc).

**Configuration:**

```yaml
servers:
  httpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090

  pop3Proxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9100
      proxyPort: 9110

  smtpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9200
      proxyPort: 9210

  canopy:
    type: canopy
    canopy:
      tigerProxyUrl: ${httpProxy.adminUrl}  # Default
      proxiedHosts:
        # HTTP routes to httpProxy
        - host: api.example.com
        - host: example.com
          matchType: SUFFIX

        # POP3 routes to pop3Proxy
        - host: mail.example.com
          tigerProxyUrl: ${pop3Proxy.adminUrl}

        # SMTP routes to smtpProxy
        - host: smtp.example.com
          tigerProxyUrl: ${smtpProxy.adminUrl}
```

**Behavior:**

- `api.example.com` → HTTP proxy (9090)
- `mail.example.com` → POP3 proxy (9110)
- `smtp.example.com` → SMTP proxy (9210)
- All routed through Canopy's single DNS server

**Best For:** Protocol-specific testing, complex backend scenarios

---

### Comparison Matrix

| Pattern           | Static              | Runtime                | Multi-Proxy               |
|-------------------|---------------------|------------------------|---------------------------|
| **Config**        | Hardcoded in YAML   | Empty, add via steps   | Per-host overrides        |
| **Routes Change** | No (restart needed) | Yes (at runtime)       | Limited (per-host prefix) |
| **Complexity**    | Low                 | Medium                 | Medium                    |
| **Use Case**      | Integration tests   | Scenario tests         | Protocol gateways         |
| **REST API**      | Not needed          | Required               | Not needed                |
| **Stage Mode**    | Can be NONE         | Usually ROUTE_PER_HOST | Usually ROUTE_PER_HOST    |

---

## DNS-Only vs Dynamic-Route Mode Deep Dive

### Understanding Control Modes

CANOPY has two operational modes affecting how it coordinates with the Tiger proxy.

**DNS-Only: `controlMode: NONE`**

- CANOPY handles DNS rewriting
- Tiger proxy is **configured separately** (static routes or other)
- Two independent systems
- DNS tells clients "use this IP for this hostname"
- Proxy must already know how to handle traffic to that IP

**Dynamic-Route: `controlMode: ROUTE_PER_HOST` (DNS + Route Registration)**

- CANOPY handles DNS rewriting **and** proxy configuration
- Every host added to CANOPY also registers a route on the Tiger proxy
- Tightly integrated
- DNS + proxy stay in sync
- If host added to CANOPY, proxy knows how to route it

### Decision Tree: Which Mode to Use?

```
Does your Tiger proxy already have the routes configured?
├─ YES → Use DNS-only (NONE)
│   Reason: Proxy already handles traffic; CANOPY just does DNS
│
└─ NO → Use Dynamic Route (ROUTE_PER_HOST)
    Should CANOPY auto-configure the proxy?
    ├─ YES → Use Dynamic Route (ROUTE_PER_HOST)
    │   Reason: Let CANOPY manage both DNS AND proxy routes
    │
    └─ NO → Configure proxy manually, then use DNS-only
        Reason: You have special routing logic that CANOPY can't handle
```

### DNS-only Example

**When to use:** Proxy has static routes configured elsewhere

```yaml
servers:
  myProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090
      proxyRoutes:
        # Routes configured here (static)
        - from: http://api.example.com
          to: http://backend-service:8080

  myCanopy:
    type: canopy
    canopy:
      controlMode: NONE              # DNS only
      tigerProxyUrl: http://myProxy:9090
      proxiedHosts:
        - host: api.example.com      # CANOPY redirects DNS to proxy
        # proxy already has route configured above
```

**Flow:**

1. Client: `nslookup api.example.com` → DNS: returns proxy IP
2. Client connects to proxy IP
3. Proxy: matches route `api.example.com` → forwards to backend

### Dynamic Route Example

**When to use:** Dynamic routes; proxy needs CANOPY to tell it what to do

```yaml
servers:
  myProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090

  myCanopy:
    type: canopy
    canopy:
      controlMode: ROUTE_PER_HOST    # DNS + routes
      tigerProxyUrl: ${myProxy.adminUrl}  # auto-wired
      proxiedHosts:
        - host: api.example.com  # CANOPY tells proxy about this
```

**Flow:**

1. CANOPY startup: registers route on proxy via admin API
2. Client: `nslookup api.example.com` → DNS: returns proxy IP
3. Client connects to proxy IP
4. Proxy: has route (via CANOPY registration) → handles it

**At Runtime (Cucumber):**

```gherkin
When TGR add the following proxied hosts to CANOPY:
| host | matchType |
| staging.api.com | EXACT |
  # CANOPY:
  # 1. Adds to DNS registry
  # 2. Registers route on proxy via admin API
  # → proxy immediately knows how to handle it
```

### Trade-offs

| Aspect              | DNS-only               | Dynamic Routes         |
|---------------------|------------------------|------------------------|
| **Setup**           | More manual            | Automatic              |
| **Runtime Changes** | Need proxy API calls   | CANOPY handles it      |
| **Complexity**      | Higher (manage both)   | Lower (CANOPY manages) |
| **Control**         | Full (explicit routes) | Less (CANOPY handles)  |
| **Dependencies**    | CANOPY on proxy        | Proxy on CANOPY        |

---

## Cucumber Integration

### Overview

CANOPY ships Cucumber glue steps that let test scenarios add/remove hosts dynamically without Java code.

**Auto-Discovery:** Steps are auto-discovered if you use `TigerCucumberRunner`. If you use custom `@CucumberOptions`,
manually add glue package:

```java
@CucumberOptions(glue = {
    "de.gematik.test.tiger.glue",
    "de.gematik.test.tiger.canopy.extension.glue"  // ← Add this
})
```

### Available Steps

#### Configuration

**Set the base URL:**

```gherkin
Given TGR set CANOPY base URL to http://canopy:8080
```

Alternative: Configure in `tiger.yaml`

```yaml
tiger:
  canopy:
    baseUrl: http://canopy:8080
```

#### Add Hosts

**Add single host (simple):**

```gherkin
When TGR add proxied host api.example.com to CANOPY
```

**Add single host with match type:**

```gherkin
When TGR add proxied host api.example.com to CANOPY with:
| matchType | EXACT |
```

**Add multiple hosts:**

```gherkin
When TGR add the following proxied hosts to CANOPY:
| host              | matchType |
| api.example.com   | EXACT     |
| *.internal.local  | SUFFIX    |
| staging.api       | EXACT     |
```

#### Query/Verify

**Check if host is registered:**

```gherkin
Then TGR CANOPY contains proxied host api.example.com
Then TGR CANOPY contains proxied host api.example.com with matchType EXACT
```

**Check if host is NOT registered:**

```gherkin
Then TGR CANOPY does not contain proxied host deleted.host
```

#### Remove Hosts

**Remove single host:**

```gherkin
When TGR remove proxied host api.example.com from CANOPY
```

**Clear all hosts:**

```gherkin
When TGR remove all proxied hosts from CANOPY
```

### Complete Example Scenario

```gherkin
Feature: API Testing with Dynamic Routing Switching

  Scenario: Switch between staging and production backends
    Given TGR set CANOPY base URL to http://canopy:8080

    # Phase 1: Test against staging
    When TGR add the following proxied hosts to CANOPY:
      | host            | matchType |
      | api.staging     | EXACT     |
      | *.staging.local | SUFFIX    |
    Then test app calls http://api.staging/health
    And response should contain "staging": true

    # Phase 2: Switch to production
    When TGR remove the following proxied hosts from CANOPY:
      | api.staging     |
      | *.staging.local |
    And TGR add the following proxied hosts to CANOPY:
      | host         | matchType |
      | api.prod     | EXACT     |
      | *.prod.local | SUFFIX    |
    Then test app calls http://api.prod/health
    And response should contain "prod": true

    # Cleanup
    When TGR remove all proxied hosts from CANOPY
```

### Best Practices

1. **Set base URL once at suite level:**

```gherkin
Feature: My Feature Suite

  Background:
    Given TGR set CANOPY base URL to http://canopy:8080

  Scenario: ...
```

2. **Use meaningful host names:**

```gherkin
# Good
When TGR add proxied host auth.staging.example.com to CANOPY

# Avoid
When TGR add proxied host temp1 to CANOPY
```

3. **Clean up after each scenario:**

```gherkin
  Scenario: ...
    # ... test steps ...

Scenario Teardown:
When TGR remove all proxied hosts from CANOPY
```

4. **Use `SUFFIX` for wildcard patterns:**

```gherkin
# Catches api.example.com, v2.api.example.com, etc.
When TGR add proxied host api.example.com to CANOPY with:
| matchType | SUFFIX |
```

---

## REST API Reference

### Overview

CANOPY exposes a REST API for programmatic host registration. Base path: `/api/v1/proxied-hosts`

### Endpoints

#### GET `/api/v1/proxied-hosts`

List all registered hosts.

**Request:** No body
**Response:** `200 OK`

```json
[
  {
    "host": "api.example.com",
    "matchType": "EXACT",
    "tigerProxyUrl": null,
    "addedAt": "2026-06-03T10:30:00Z"
  },
  {
    "host": "example.org",
    "matchType": "SUFFIX",
    "tigerProxyUrl": "http://alt-proxy:9100/",
    "addedAt": "2026-06-03T10:31:00Z"
  }
]
```

#### POST `/api/v1/proxied-hosts`

Add a single host (idempotent).

**Request:**

```json
{
  "host": "api.example.com",
  "matchType": "EXACT",
  "tigerProxyUrl": null
  // optional: per-host proxy override
}
```

**Response:** `201 Created`

```json
{
  "host": "api.example.com",
  "matchType": "EXACT",
  "tigerProxyUrl": null,
  "addedAt": "2026-06-03T10:30:00Z"
}
```

#### POST `/api/v1/proxied-hosts/bulk`

Add multiple hosts atomically.

**Request:**

```json
{
  "hosts": [
    {
      "host": "api.example.com",
      "matchType": "EXACT"
    },
    {
      "host": "example.org",
      "matchType": "SUFFIX"
    },
    {
      "host": "staging.api",
      "matchType": "EXACT",
      "tigerProxyUrl": "http://alt:9100/"
    }
  ]
}
```

**Response:** `200 OK`

```json
{
  "added": [
    {
      "host": "api.example.com",
      "matchType": "EXACT",
      ...
    },
    {
      "host": "example.org",
      "matchType": "SUFFIX",
      ...
    }
  ],
  "unchanged": [
    {
      "host": "staging.api",
      "matchType": "EXACT",
      ...
    }
  ]
}
```

#### DELETE `/api/v1/proxied-hosts/{host}`

Remove a specific host.

**Request:** No body
**Response:** `204 No Content`

**Example:**

```bash
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts/api.example.com
```

#### DELETE `/api/v1/proxied-hosts`

Clear all registered hosts.

**Request:** No body
**Response:** `204 No Content`

```bash
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts
```

#### GET `/api/v1/proxied-hosts/config`

Retrieve current configuration.

**Response:** `200 OK`

```json
{
  "tigerProxyUrl": "http://tiger-proxy:9090/",
  "controlMode": "ROUTE_PER_HOST",
  "dnsPort": 53
}
```

#### PUT `/api/v1/proxied-hosts/config/proxy-url`

Change the Tiger proxy URL at runtime.

**Request:**

```json
{
  "url": "http://new-proxy:9100/"
}
```

**Response:** `200 OK`

```json
{
  "tigerProxyUrl": "http://new-proxy:9100/",
  "controlMode": "ROUTE_PER_HOST",
  "dnsPort": 53
}
```

#### GET `/actuator/health`

Health check endpoint.

**Response:** `200 OK`

```json
{
  "status": "UP",
  "components": {
    "dns": {
      "status": "UP",
      "details": {
        "bound": true,
        "port": 53
      }
    }
  }
}
```

### Example Workflows

**Bulk add + verify:**

```bash
# Add multiple hosts
curl -X POST http://canopy:8080/api/v1/proxied-hosts/bulk \
  -H 'Content-Type: application/json' \
  -d '{
    "hosts": [
      {"host": "api.example.com"},
      {"host": "auth.example.com"}
    ]
  }'

# List what's registered
curl http://canopy:8080/api/v1/proxied-hosts

# Test DNS (from inside a container)
nslookup api.example.com canopy
```

**Runtime proxy swap:**

```bash
# Switch which proxy handles traffic
curl -X PUT http://canopy:8080/api/v1/proxied-hosts/config/proxy-url \
  -H 'Content-Type: application/json' \
  -d '{"url": "http://backup-proxy:9090/"}'
```

**Error handling:**

```bash
# Invalid host name → 400 Bad Request
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
  -d '{"host": "", "matchType": "EXACT"}'
# Response: 400 - "host must not be blank"

# Duplicate add → 201 Created (idempotent)
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
  -d '{"host": "api.example.com"}'
# First call: 201
# Second call: 201 (same response, not an error)

# Remove non-existent host → 204 No Content (idempotent)
curl -X DELETE http://canopy:8080/api/v1/proxied-hosts/never-added
# Response: 204 (no error)
```

---

## Troubleshooting

### Issue 1: DNS Resolution Not Working

**Symptom:** Containers can't resolve proxied hostnames; `nslookup` returns NXDOMAIN or wrong IP

**Diagnosis Steps:**

1. **Is CANOPY running?**
   ```bash
   docker ps | grep canopy
   docker logs <canopy-container>
   ```
   If missing, start it. If failed startup, check logs for binding errors (port 53?).

2. **Is the container using CANOPY as DNS?**
   ```bash
   docker inspect <app-container> | grep Dns
   # Should show canopy's IP or "canopy" name
   ```
   If not, add `dns: [canopy]` or run with `-e CANOPY_ENABLED=true`.

3. **Is the host registered with CANOPY?**
   ```bash
   curl http://canopy:8080/api/v1/proxied-hosts | grep "your-hostname"
   ```
   If missing, add it:
   ```bash
   curl -X POST http://canopy:8080/api/v1/proxied-hosts \
     -d '{"host": "your-hostname"}'
   ```

4. **Does CANOPY have a proxy configured?**
   ```bash
   curl http://canopy:8080/api/v1/proxied-hosts/config
   # Check tigerProxyUrl is not empty
   ```
   If empty, set it:
   ```bash
   curl -X PUT http://canopy:8080/api/v1/proxied-hosts/config/proxy-url \
     -d '{"url": "http://proxy:9090/"}'
   ```

5. **Test DNS from inside container:**
   ```bash
   docker exec <app-container> nslookup your-hostname canopy
   # Should return proxy IP (not NXDOMAIN)
   ```

**Quick Fix Sequence:**

```bash
# 1. Verify CANOPY is up
curl http://canopy:8080/actuator/health

# 2. Verify host is registered
curl http://canopy:8080/api/v1/proxied-hosts | jq .

# 3. Add if missing
curl -X POST http://canopy:8080/api/v1/proxied-hosts \
  -d '{"host": "your-hostname"}'

# 4. Test from app
docker exec <app> nslookup your-hostname canopy
```

---

### Issue 2: Health Check Timeout (CANOPY Fails to Start)

**Symptom:** Container start hangs for 60 seconds, then fails with health check timeout

**Diagnosis:**

1. **Check CANOPY logs:**
   ```bash
   docker logs <canopy-container>
   # Look for errors in Spring Boot startup
   ```

2. **Common causes:**
    - Port 53 already in use → use ephemeral port (`dnsPort: 5353`)
    - Missing Tiger proxy URL → set `TIGER_PROXY_URL` env var
    - DNS initialization slow → wait longer (normal on first start)

3. **Check if port 53 is free:**
   ```bash
   netstat -an | grep 53
   # If in use, either stop other process or change port
   ```

4. **Verify REST API responds:**
   ```bash
   curl -v http://canopy:8080/actuator/health
   # Should return 200 quickly
   ```

**Quick Fix:**

```yaml
# Use unprivileged port if 53 is blocked
canopy:
  dnsPort: 5353  # Use this instead of 53
```

Then update container DNS references:

```bash
docker run --dns=canopy:5353 my-app
```

---

### Issue 3: Port Already in Use

**Symptom:**

- Port 53 TCP/UDP: `bind: permission denied` or `already in use`
- Port 8080: REST API unreachable

**Solutions:**

**Option A: Use Unprivileged Port**

```yaml
canopy:
  adminPort: 8090      # REST API on 8090 instead of 8080
  dnsPort: 5353        # DNS on 5353 instead of 53
```

**Option B: Free Up Port (if something else has it)**

```bash
# Find what's using port 53
sudo lsof -i :53

# Kill the process
kill -9 <pid>

# Then restart CANOPY
```

**Option C: Run as Root (Not Recommended)**

```bash
# Requires CAP_NET_BIND_SERVICE capability
# Official image handles this safely; custom images need:
docker run --cap-add=NET_BIND_SERVICE canopy:latest
```

---

### Issue 4: Placeholder Resolution Failed

**Symptom:** Tiger can't resolve `${myCanopy.baseUrl}` or other placeholders

**Diagnosis:**

1. **Is the canopy server defined?**
   ```yaml
   servers:
     myCanopy:
       type: canopy
       # ...
   ```

2. **Is the placeholder spelled correctly?**
   ```yaml
   # Correct
   ${myCanopy.baseUrl}
   ${myCanopy.dnsAddress}
   ${myCanopy.dnsPort}
   ${myCanopy.containerName}

   # Common typos
   ${myCanopy.url}        # ← Wrong (baseUrl, not url)
   ${canopy.baseUrl}      # ← Wrong (use server ID, not type)
   ${MyCanopy.baseUrl}    # ← Wrong (case sensitive)
   ```

3. **Has CANOPY started before the placeholder is evaluated?**
    - YAML config parsing happens after startup ordering
    - If placeholder used in another server starting immediately, it might not exist yet
    - Add explicit `dependsUpon: myCanopy` to use the placeholder in that server

**Quick Fix:**

```yaml
servers:
  myCanopy:
    type: canopy
    canopy:
      proxiedHosts:
        - host: example.com

  testApp:
    type: docker
    dependsUpon: myCanopy              # ← Ensure canopy starts first
    docker:
      image: my-app
      environment:
        CANOPY_URL: ${myCanopy.baseUrl}  # Now resolved correctly
```

---

### Issue 5: Dynamic Route Registration Failure

**Symptom:** Hosts added to CANOPY but Tiger proxy doesn't route; error in logs like "PUT /route failed"

**Diagnosis:**

1. **Is controlMode set to ROUTE_PER_HOST?**
   ```yaml
   canopy:
     controlMode: ROUTE_PER_HOST  # Must be this for Stage 2
   ```

2. **Can CANOPY reach the proxy admin API?**
   ```bash
   curl http://proxy:9000/route  # Should be reachable
   ```

3. **Were hosts added correctly?**
   ```bash
   curl http://canopy:8080/api/v1/proxied-hosts
   # Should show your hosts
   ```

4. **Check CANOPY logs for registration errors:**
   ```bash
   docker logs <canopy-container> | grep -i route
   # Should NOT show errors
   ```

**Quick Fix:**

- Ensure proxy is running before adding hosts
- Check proxy admin port is correct in `tigerProxyUrl`
- Check network connectivity between CANOPY and proxy containers

---

### Issue 6: Configuration Mistakes

**Common mistakes and fixes:**

| Mistake                            | Error               | Fix                                                  |
|------------------------------------|---------------------|------------------------------------------------------|
| Host name typo                     | DNS says NXDOMAIN   | Verify with `curl .../proxied-hosts`                 |
| Empty proxiedHosts                 | Nothing gets routed | Add hosts via Cucumber or REST API                   |
| NONE mode, proxy fails             | Routes don't work   | Switch to ROUTE_PER_HOST or configure proxy manually |
| Multiple proxies, no tigerProxyUrl | Auto-wire skipped   | Set tigerProxyUrl explicitly                         |
| Port out of range                  | Start fails         | Use port 0-65535                                     |
| controlMode typo                   | Start fails         | Use NONE or ROUTE_PER_HOST (exact)                   |

---

## Complete End-to-End Examples

### Example 1: Pure Docker Setup

**Files:** `docker-compose.yaml`

```yaml
version: '3.8'

services:
  tiger-proxy:
    image: gematik1/tiger-proxy-image:latest
    container_name: tiger-proxy
    ports:
      - "9000:9000"   # Admin UI
      - "9090:9090"   # Proxy port
    networks:
      - test-network
    environment:
      TIGER_PROXY_PORT: 9090
      TIGER_PROXY_ADMIN_PORT: 9000

  canopy:
    image: gematik1/tiger-canopy-image:latest
    container_name: canopy
    ports:
      - "53:53/udp"
      - "53:53/tcp"
      - "8080:8080"   # REST API
    networks:
      - test-network
    environment:
      TIGER_PROXY_URL: "http://tiger-proxy:9090/"
      CANOPY_DNS_PORT: "53"
      CANOPY_CONTROL_MODE: "ROUTE_PER_HOST"
      CANOPY_PROXIEDHOSTS_0_HOST: "api.example.com"
      CANOPY_PROXIEDHOSTS_0_MATCHTYPE: "EXACT"
      CANOPY_PROXIEDHOSTS_1_HOST: "example.org"
      CANOPY_PROXIEDHOSTS_1_MATCHTYPE: "SUFFIX"
    depends_on:
      - tiger-proxy

  # Helper sidecar: resolv-writer (writes ./dns/resolv.conf)
  resolv-writer:
    image: alpine:3
    command: ["sh", "-c", "for i in $(seq 1 30); do IP=$(getent hosts canopy | awk '{print $1}') && [ -n \"$IP\" ] && break; sleep 1; done; \
      if [ -z \"$IP\" ]; then echo 'ERROR: could not resolve canopy' >&2; exit 1; fi; \
      cat > /dns/resolv.conf <<EOF\n+nameserver $IP\nnameserver 127.0.0.11\n+EOF\n+      tail -f /dev/null"]
    networks:
      - test-network
    depends_on:
      - canopy
    volumes:
      - ./dns:/dns
    healthcheck:
      test: ["CMD-SHELL","[ -s /dns/resolv.conf ] && grep -q nameserver /dns/resolv.conf || exit 1"]
      interval: 5s
      timeout: 2s
      retries: 5

  test-app:
    image: my-test-app:latest
    container_name: test-app
    networks:
      - test-network
    volumes:
      - ./dns/resolv.conf:/etc/resolv.conf:ro
    depends_on:
      resolv-writer:
        condition: service_healthy
    command: pytest tests/

networks:
  test-network:
    driver: bridge
```

**Verify:**

```bash
# Start
docker compose up -d

# Test DNS (app automatically discovered Canopy's IP)
docker compose exec test-app nslookup api.example.com
# Output: <canopy-ip> from answer section

# Add host at runtime
curl -X POST http://localhost:8080/api/v1/proxied-hosts \
  -d '{"host": "staging.api", "matchType": "EXACT"}'

# Stop
docker compose down
```

# List hosts

curl http://localhost:8080/api/v1/proxied-hosts | jq .

# Stop

docker compose down

```

---

### Example 2: Tiger Integration (Simple)

**Files:** `pom.xml`, `tiger.yaml`

**pom.xml:**

```xml

<dependency>
    <groupId>de.gematik.test</groupId>
    <artifactId>tiger-canopy-extension</artifactId>
    <version>${tiger.version}</version>
</dependency>
```

**tiger.yaml:**

```yaml
localProxyActive: true

servers:
  myProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090
      proxyRoutes: [ ]  # Let canopy manage routes

  myCanopy:
    type: canopy
    canopy:
      # tigerProxyUrl auto-wired to ${myProxy.adminUrl}
      # controlMode auto-set to ROUTE_PER_HOST
      proxiedHosts:
        - host: api.example.com
          matchType: EXACT
        - host: internal.local
          matchType: SUFFIX

  testDatabase:
    type: docker
    docker:
      image: postgres:15
      exposedPorts: [ 5432 ]
    environment:
      - POSTGRES_USER=testuser
      - POSTGRES_PASSWORD=testpass
    exports:
      - DB_URL=jdbc:postgresql://testDatabase:5432/testdb

  testApplication:
    type: externalJar
    dependsUpon:
      - myCanopy           # Ensure DNS is ready
      - testDatabase       # Ensure DB is ready
    source:
      - local:target/my-app.jar
    healthcheckUrl: http://127.0.0.1:8080/health
    externalJarOptions:
      options:
        - -Xmx512m
    environment:
      - API_URL=http://api.example.com  # Will be intercepted
      - DB_URL=${testDatabase.DB_URL}
```

**Test (MyApiIT.java):**

```java

@RunWith(TigerCucumberRunner.class)
@CucumberOptions(features = "src/test/resources/features")
public class MyApiIT {
  // Tests run with CANOPY routing active
}
```

**Feature (test.feature):**

```gherkin
Given TGR set CANOPY base URL to http://myCanopy:8080

Scenario: API test with dynamic routing
When TGR add the following proxied hosts to CANOPY:
| host | matchType |
| api.example.com | EXACT |

  # App connects to api.example.com
  # CANOPY redirects DNS to proxy
  # Proxy routes to backend (auto-configured by CANOPY)

When I call the API via http://api.example.com/users
Then I should get a valid response

When TGR remove proxied host api.example.com from CANOPY
```

---

### Example 3: Multi-Proxy Setup

**tiger.yaml:**

```yaml
localProxyActive: true

servers:
  # Three specialized proxies
  httpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9000
      proxyPort: 9090

  pop3Proxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9100
      proxyPort: 9110

  smtpProxy:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9200
      proxyPort: 9210

  # CANOPY fans traffic to all three
  canopy:
    type: canopy
    canopy:
      tigerProxyUrl: ${httpProxy.adminUrl}  # Default
      proxiedHosts:
        # HTTP routes to httpProxy
        - host: api.example.com
          matchType: EXACT
        - host: example.com
          matchType: SUFFIX

        # POP3 routes to pop3Proxy
        - host: mail.example.com
          tigerProxyUrl: ${pop3Proxy.adminUrl}

        # SMTP routes to smtpProxy
        - host: smtp.example.com
          tigerProxyUrl: ${smtpProxy.adminUrl}
```

**Result:**

- `api.example.com` → HTTP proxy (9090) → backend
- `mail.example.com` → POP3 proxy (9110) → mail server
- `smtp.example.com` → SMTP proxy (9210) → SMTP server

All routed through CANOPY's single DNS server.

---

##Architecture & Design

### Component Overview

```
Application Container
├─ DNS query: "resolve api.example.com"
│
└─► CANOPY DNS Server (UDP:53, TCP:53)
    ├─ (1) Check: is api.example.com in proxiedHosts registry?
    │   └─ YES → return Tiger proxy IP (DNS A/AAAA record)
    │
    └─ NO → forward to upstream DNS resolver
         └─ return real IP from upstream
```

### Performance Characteristics

- **DNS lookups:** <1ms for exact matches, <5ms for suffix matching
- **Registry add/remove:** <10ms (async proxy updates don't block)
- **REST API:** <50ms typical (includes async tasks)
- **Memory:** ~10MB base + 100KB per 1000 hosts
- **Throughput:** >10,000 queries/sec per CPU core (typical modern hardware)

### Security

- **No authentication:** REST API is unsecured (runs inside testenv network)
- **Port 53 privilege:** Requires CAP_NET_BIND_SERVICE on Linux (official image has this)
- **DNS spoofing:** Only affects containers using CANOPY as DNS; external queries unaffected
- **Network isolation:** Runs in Docker network; unreachable from outside

### Limitations

- **Single DNS server per testenv:** Multi-CANOPY future work
- **In-memory registry:** Lost on restart (acceptable for tests)
- **No persistence:** Perfect for ephemeral test environments
- **No clustering:** Each container is independent
- **No service discovery:** Requires manual host registration

---

## Further Reading

**In this repository:**

- [README.md](../README.md) — API reference, quick start, configuration table
- [ADR](../../doc/adr/canopy.md) — Architecture decision record, design details

**Using CANOPY:**

- [Tiger User Manual](../../../doc/user_manual/tigerTestEnvironmentManager.adoc) — Integration with Tiger testenv-mgr

**External Resources:**

- [Tiger Test Framework](https://github.com/gematik/app-Tiger/) — Main Tiger repository
- [Docker Compose](https://docs.docker.com/compose/) — Docker networking reference
- [dnsjava](http://www.dnsjava.org/) — Java DNS library powering CANOPY

---

## Checklists

### Pre-Setup Checklist

- [ ] Docker and docker-compose installed (or Tiger testenv-mgr)
- [ ] Port 53 (UDP/TCP) available on host (or plan to use ephemeral port)
- [ ] Port 8080 available for REST API (or configure alternates)
- [ ] Tiger proxy running (if using Stage 2 mode)
- [ ] Familiar with DNS basics (CNAME, A records, etc.)
- [ ] Network connectivity verified between containers

### Tiger Integration Setup Checklist

- [ ] `tiger-canopy-extension` added to POM dependencies
- [ ] `type: canopy` server defined in `tiger.yaml`
- [ ] Exactly one `type: tigerProxy` sibling (for auto-wiring) OR explicit `tigerProxyUrl` set
- [ ] `proxiedHosts` list populated with initial hosts (or left empty for runtime config)
- [ ] `dependsUpon: myCanopy` added to any server using `${myCanopy.xxx}` placeholders
- [ ] Test app has `dns: [canopy]` or uses Tiger DNS auto-injection
- [ ] `mvn clean verify` runs without errors
- [ ] Logs show: "CANOPY running, admin=http://localhost:8080, dns=172.20.0.X:53"

### Troubleshooting Checklist

When DNS/routing isn't working:

- [ ] CANOPY container is running (`docker ps`)
- [ ] No startup errors in logs (`docker logs canopy`)
- [ ] Host is registered (`curl .../proxied-hosts`)
- [ ] Proxy is reachable (`curl proxy:9000/xyz`)
- [ ] Container uses correct DNS server (`docker inspect app | grep Dns`)
- [ ] Test DNS manually (`docker exec app nslookup host canopy`)
- [ ] Health check passes (`curl .../ actuator/health`)

---

**Last Updated:** June 3, 2026
**Version:** 1.0
**Audience:** All CANOPY users
**Questions?** See FAQ.md or open a GitHub issue
