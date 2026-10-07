# PCAP Capture Configuration and Usage

## Overview

Tiger automatically captures Tiger Proxy's traffic in-process using `pcap4j` and `libpcap`. Capture
is protocol-agnostic: it's a raw TCP packet capture filtered by port (via BPF), not an HTTP parser —
it captures every byte on a proxied port exactly as it is on the wire, whether that's HTTP, HTTPS/TLS,
or any other (including binary/proprietary) protocol Tiger Proxy happens to be forwarding on that
port.

Capture modes:
- **Local**: One or more local interfaces (default: loopback), merged per scenario
- **Distributed**: Remote Tiger proxies with clock offset compensation, merged into the same
  per-scenario file as local capture

Each test scenario generates `.pcapng` (Next Generation Pcap) files suitable for inspection in Wireshark, containing the complete exchange including TLS handshakes, redirects, retries, and any protocol-level details — for whatever protocol was actually running on the captured port.

## Quick Start

1. **Enable in tiger.yaml**:
   ```yaml
   lib:
     pcapCapture:
       enabled: true
   ```

2. **Install native library** (one-time per OS):
   - **Windows**: [Npcap](https://npcap.com/) in WinPcap-compatible mode
   - **Linux**: `libpcap-dev` package + capabilities or sudo
   - **macOS**: Wireshark (includes ChmodBPF) or manual BPF group setup

3. **Run tests normally**. Files appear in `target/evidences/` as `<scenarioId>.pcapng` (or
   `suite_<epochMillis>.pcapng` if `splitByTestcase: false`) — see [File Locations and
   Naming](#file-locations-and-naming).

4. **Open in Wireshark**: `File → Open` → select `.pcapng` → inspect flows, decode payloads, search packets.

## Capture Modes

### Local (Default)
- **What**: Captures from a single local loopback interface
- **When to use**: Simple tests, local-only environments, single proxy instance
- **Setup**: Set `enabled: true` in tiger.yaml
- **Output**: One `.pcapng` file per scenario in `target/evidences/`

### Multi-Interface Local Capture
- **What**: Captures simultaneously from as many local interfaces as you list — no separate mode to
  enable, just list more than one interface
- **When to use**: Multi-interface capture on same node, container bridges, all loopback variations
- **Setup**: Set `interfaceNames` to a list of interface names (e.g., `["lo", "eth0", "docker0"]`)
- **Output**: One `.pcapng` file per scenario, with every interface's traffic merged into it by
  timestamp automatically
- **Benefit**: Complete cross-interface visibility in one file, safe under parallel test execution
  (see [Parallel Test Execution](#parallel-test-execution))

### Distributed Capture
- **What**: Captures on remote Tiger proxy instances with automatic clock offset compensation
- **When to use**: Multi-host deployments, distributed test environments, cloud infrastructure
- **Setup**: Enable `remoteProxies: true` in tiger.yaml
- **Output**: Remote PCAP files downloaded, merged with local capture
- **Benefit**: Complete end-to-end visibility across all proxy instances

## Configuration Reference

All settings live under `lib.pcapCapture.*`:

| Setting | Type | Default | Purpose |
|---------|------|---------|---------|
| `enabled` | boolean | `false` | Master switch; without this, no capture threads start. |
| `startSuspended` | boolean | `false` | Start each scenario with capture suspended (`true`) or enabled (`false`). Use with BDD steps to control when recording begins. |
| `splitByTestcase` | boolean | `true` | One file per scenario (`true`) vs. one file for the entire suite (`false`). |
| `snaplenKb` | integer | `64` | Per-packet bytes copied to userspace (1 KB = 1024 B). Local loopback MTU is typically 64–65 KB; reduce if disk space is tight. |
| `bufferSizeKb` | integer | `16384` | Kernel ring buffer size in KB. Larger absorbs JVM/GC pauses; smaller risks silent packet loss. |
| `filename` | string | `${scenarioId}.pcapng` | Filename template for the per-testcase file when `splitByTestcase=true`; `${scenarioId}` is substituted with the real scenario id. |
| `bpfFilter` | string | *(none)* | Manual BPF filter expression (e.g. `"tcp port 8080 or tcp port 9090"`), passed verbatim to the capture. When set, this replaces automatic port discovery entirely. |
| `interfaceNames` | list | `["lo"]` (loopback only) | Network interfaces to capture on. `null` or empty defaults to loopback. Single interface: `["eth0"]`. Multiple interfaces (all captured): `["lo", "eth0", "docker0"]`. When multiple specified, each gets its own capture thread; all merged per scenario. |
| `remoteProxies` | boolean | `false` | Enable distributed capture on remote Tiger proxies with clock offset compensation. |
| `dropDuplicatePackets` | boolean | `false` | Write a packet once that two of the merged sources captured, instead of twice — see [Packets Captured Twice](#packets-captured-twice). Duplicates are always reported with a warning; this only drops them. |
| `mergeRemotePcaps` | boolean | `true` | Merge the downloaded remote files into the local scenario file (`true`), or only download them. |
| `remoteDownloadTimeoutSeconds` | integer | `30` | Timeout for every call to a remote proxy — starting, stopping, suspending and resuming a capture, and downloading its file. A proxy that stops answering is given up on after this, rather than hanging the scenario. |

### Example Configurations

**Basic Local Capture**:
```yaml
lib:
  pcapCapture:
    enabled: true                          # Enable capture
    splitByTestcase: true                  # One file per scenario
    snaplenKb: 64                          # Capture full 64 KB frames
    bufferSizeKb: 16384                    # 16 MB kernel buffer
    filename: ${scenarioId}.pcapng         # Per-testcase filename
```

**Multi-Interface Local Capture**:
```yaml
lib:
  pcapCapture:
    enabled: true
    splitByTestcase: true
    interfaceNames:                        # Capture from multiple interfaces
      - "lo"       # Loopback
      - "eth0"     # Primary interface
      - "docker0"  # Docker bridge
```

**Distributed Capture**:
```yaml
lib:
  pcapCapture:
    enabled: true
    remoteProxies: true                    # Capture from remote proxies
    splitByTestcase: true
    remoteDownloadTimeoutSeconds: 30       # Timeout for every call to a remote proxy
```

Each remote proxy must also opt in to being captured on remotely, see
[Configuring Capture per Remote Proxy](#configuring-capture-per-remote-proxy).

## Runtime Control via BDD Steps

You can pause and resume packet capture during test execution using BDD steps. This is useful for excluding setup traffic or focusing on specific test scenarios.

```gherkin
Scenario: Capture only critical operations
  Given pcap capture is enabled
  When I perform authentication setup
  And PCAP capture is suspended
  And I send unimportant cleanup traffic
  And PCAP capture resumes
  Then critical API calls will be captured in the pcap file
```

### Available Steps

- **`PCAP capture is suspended`** — pause packet recording until explicitly resumed
- **`PCAP capture resumes`** — resume packet recording with current baseline configuration

### Behavior

- **Per-scenario**, even under parallel test execution: `PCAP capture is suspended`/`resumes` in one
  scenario never affects another scenario running concurrently.
- **Covers local and remote**: when `remoteProxies: true` is also enabled, these steps pause/resume
  recording on this scenario's remote proxy captures too, not just local capture.
- **Baseline state**: each scenario starts suspended or capturing according to `startSuspended` (see
  Configuration Reference below).
- **No traffic, no file**: a scenario that starts suspended and never calls `PCAP capture resumes`
  produces no `.pcapng` file at all — not even an empty one.

### Configuration for Selective Capture

```yaml
lib:
  pcapCapture:
    enabled: true           # Enable capture infrastructure
    startSuspended: true    # Start each scenario suspended; resume via steps
```

With this configuration:
- Each scenario begins with capture suspended
- Use `PCAP capture resumes` to start recording when needed
- Recording stops when the scenario ends, scenario starts clean next time

## Multi-Interface Local Capture

When `interfaceNames` includes multiple interfaces, Tiger automatically captures simultaneously from
all specified interfaces on the same node — there is no limit on how many, and no extra
configuration beyond listing them. Every interface's traffic is merged, sorted by timestamp, into
that scenario's single `.pcapng` file — no manual merge step.

**Configuration example:**
```yaml
lib:
  pcapCapture:
    enabled: true
    interfaceNames:
      - "lo"         # Loopback
      - "eth0"       # Main interface
      - "docker0"    # Docker bridge
```

**Benefits:**
- See all proxy traffic in one file without a manual merge step
- No clock offset handling needed (same system clock)
- Graceful degradation: if one interface fails to open, capture proceeds on the rest
- Safe under parallel test execution — see [Parallel Test Execution](#parallel-test-execution)

## Distributed Capture

When `remoteProxies: true` is enabled, Tiger coordinates PCAP capture across multiple remote Tiger
proxy instances. At scenario end, each remote proxy's file is downloaded and merged — in the same
pass as the local interfaces' captures, sorted by timestamp with automatic clock offset compensation
— directly into that scenario's one `.pcapng` file. There is no separate "remote" file to find.

This is safe under parallel test execution, including when two scenarios capture through the same
remote proxy at the same time — see [Parallel Test Execution](#parallel-test-execution). The `PCAP
capture is suspended`/`resumes` BDD steps (see [Runtime Control via BDD
Steps](#runtime-control-via-bdd-steps)) pause and resume remote recording too, not just local.

Traffic outside any scenario's window is also captured remotely, the same way the local [gap
file](#file-locations-and-naming) works — see that section.

### Chained / Mesh Proxies

If a remote proxy itself has its own downstream remote proxies configured (its own
`trafficEndpoints`), pcap capture relays automatically through the whole chain: starting,
stopping, suspending, and resuming a capture on proxy B also relays to whatever proxies B itself
talks to, and so on at every further hop. Each proxy only needs to know its own direct neighbor —
the local test suite never needs to know the full topology, since every hop does this the same way
B did, cascading as deep as the chain goes.

When a capture on B is stopped, B downloads and merges each of its own downstream proxies' files
(clock-offset compensated) into its own file *before* handing it back up — so from the test suite's
point of view, stopping a capture still produces exactly one file per directly-configured remote
proxy, just now transparently containing that proxy's entire downstream subtree. A proxy that fails
to relay to one of its own downstream neighbors logs a warning and continues; it never fails the
capture on the proxies that did respond.

**Configuration example:**
```yaml
lib:
  pcapCapture:
    enabled: true
    remoteProxies: true
    remoteDownloadTimeoutSeconds: 30
```

**Benefits:**
- Complete end-to-end visibility across distributed proxies, local interfaces, and every combination
  of the two, in a single file per scenario
- Automatic clock offset compensation for accurate timeline
- If the remote merge fails (e.g. a download error), the local interfaces' capture is not lost — it
  stays as temporary files for manual recovery rather than being silently discarded

### Configuring Capture per Remote Proxy

Every remote proxy can capture something different, and only your test suite knows what it wants
captured. So the capture settings — which interfaces to capture on, a manual BPF filter, snaplen and
buffer size — are set per proxy, under `pcapCapture` in that proxy's own `servers:` entry in your
`tiger.yaml`. The test suite sends them to the proxy with every capture start.

```yaml
servers:
  remoteProxy1:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9011   # matches this entry to the proxy the suite talks to
      pcapCapture:
        interfaceNames:
          - "eth0"
          - "docker0"
        bpfFilter: "tcp port 8080 or tcp port 9090"  # optional; bypasses automatic port discovery
        snaplenKb: 64
        bufferSizeKb: 16384
  remoteProxy2:
    type: tigerProxy
    tigerProxyConfiguration:
      adminPort: 9012
      pcapCapture:
        interfaceNames:
          - "lo"
```

- **Opt-in**: a proxy only accepts capture requests if its own configuration has a `pcapCapture`
  block — an empty `pcapCapture: {}` is enough. Capturing the packets of the host a proxy runs on
  is not something to hand to everyone who can reach its admin port, so without the block the proxy
  answers HTTP 403 and the test suite logs which proxy refused. For a proxy started from a
  `servers:` entry, the block you write there is that configuration.
- **What a caller may ask for is limited by the proxy's own `pcapCapture` block** — the one that
  opted it in. The block a suite sends only ever narrows or matches what that block allows, and a
  request outside it is refused with HTTP 403 and a message saying what is allowed:
  - *Interfaces*: only those in the block's `allowedInterfaces`, else those in its own
    `interfaceNames` (naming an interface to capture on is allowing it, so a `servers:` entry that
    lists its interfaces needs nothing more), else loopback only. `"*"` is only allowed if
    `allowedInterfaces` contains `"*"`.
  - *Filter*: a caller's own `bpfFilter` is only accepted if it equals the block's own `bpfFilter`,
    or the block sets `allowCustomFilter: true`. Without a filter a capture only sees the proxy's
    ports; a filter such as `tcp` would capture all traffic on the allowed interfaces.
  - *Kernel buffer*: at most 256 MB (or the block's own `bufferSizeKb`, if that is larger).

  `allowedInterfaces` and `allowCustomFilter` are read from the proxy's own configuration only; if a
  request carries them they are ignored.

  ```yaml
  tigerProxy:                 # an externally managed proxy's own configuration
    pcapCapture:
      allowedInterfaces: ["lo", "eth0"]   # suites may capture on these, and on nothing else
      allowCustomFilter: false            # default: suites cannot send filters of their own
  ```
- If the suite has no matching entry to take settings from (it logs this), it sends nothing for that
  proxy, which then uses its own `tigerProxy.pcapCapture.*`, and without those, loopback only with
  the same defaults as local capture (64 KB snaplen, 16 MB buffer).
- A proxy that is only connected to by URL (`trafficEndpoints`) has no `servers:` entry to carry the
  block, so it can only be configured — and opted in — through its own `tigerProxy.pcapCapture.*`.
- A remote proxy leaves its own admin port out of the capture: it carries the start/stop calls and
  file downloads of the captures themselves, which would otherwise end up in them. Use `bpfFilter`
  if you do want it.
- Several test suites can use one proxy at once. A suite's gap capture (the traffic outside any of
  its scenarios) only pauses while *that suite's* scenarios are running, not another suite's.
- A remote proxy deletes a capture's file once it has been downloaded; one that is never downloaded
  is deleted after an hour.
- A capture is addressed by the random `captureId` the proxy returns when it starts; whoever knows
  it can stop, query or download that capture. Ask the proxy's operator to keep the admin port
  reachable only for those who may capture.
- Settings apply per capture, so different clients can ask the same proxy for different
  things at the same time: each capture only gets the interfaces and traffic its own settings
  select. Interfaces not yet being captured are opened when a capture needs them.
- The exceptions are `snaplenKb` and `bufferSizeKb`: both are fixed when an interface is first
  opened. A capture asking for another snaplen or buffer size on an interface that is already being
  captured gets the existing one, and a warning is logged.
- Downstream proxies a remote proxy relays to are not sent these settings: each uses its own
  configuration.

### Packets Captured Twice

When files are merged, Tiger recognises a packet that two of the sources captured: the same frame,
byte for byte, captured within a few milliseconds by two *different* sources. That happens when the
same traffic is captured twice — by your local capture and by a Tiger proxy running on the same
machine (the proxy then sees the machine's loopback just like the local capture does), or on two
interfaces of one host, such as a bridge and its veth.

Tiger counts these and logs a warning once it finds a few. By default every copy stays in the merged
file; set `dropDuplicatePackets: true` to keep one. Identical packets within one source are never
dropped (they happened twice on the wire), and neither are retransmissions, which come later than
the window.

Traffic seen through NAT — by a proxy inside a docker container, say — is not recognised: its
addresses, ports and MACs differ from the host's view of the same traffic, so it is not
byte-identical. It is a different observation of that traffic, and keeping both is usually what you
want.

## What Gets Captured

Tiger automatically discovers all Tiger Proxy instances in your test environment and applies a BPF filter to capture traffic on:

1. **Proxy listening port** (e.g., 8080) — client ↔ proxy traffic
2. **Proxy admin port** (e.g., 9000) — admin API calls, metrics, health checks
3. **Route target ports** — all backend/upstream server ports defined in proxy routes

**Example**: If your proxy routes `/api` to `localhost:3000` and `/health` to `localhost:5000`, capture includes traffic on ports 8080 (proxy), 9000 (admin), 3000, and 5000.

**Proxies started mid-run**: a `TigerProxyServer` started after capture begins (e.g. via `TGR start server`) is picked up automatically. If you need full control over the filter instead, set `bpfFilter` to bypass automatic discovery entirely.

A route target without an explicit port counts as its scheme's default (`https://host` is port 443).
A remote proxy's admin port is not captured, see [Configuring Capture per Remote
Proxy](#configuring-capture-per-remote-proxy).

This gives you **complete end-to-end visibility** of request/response flows without manual configuration.

## File Locations and Naming

- **Output directory**: `target/evidences/`
- **Filename pattern** (per scenario, `splitByTestcase=true`): the `filename` template with
  `${scenarioId}[_<variant>].pcapng`
  - `${scenarioId}` — the real Tiger scenario id
  - `variant` — data variant index, appended if the scenario is parametrized
  - Names longer than 30 characters are truncated with an 8-char UUID-hash suffix so long scenario
    ids stay unique instead of colliding
  - A custom template without `${scenarioId}` produces the same filename for every scenario in
    the run — include it unless you intend that.
- **Suite-wide file** (if `splitByTestcase=false`): `suite_<epochMillis>.pcapng` for the whole run.
- **Gap file** (if `splitByTestcase=true`): one `suite_<epochMillis>.pcapng` file catching traffic
  outside any scenario's capture window (before the first scenario, any gap between scenarios, after
  the last one). Only appears if it actually captured something. When `remoteProxies: true` is also
  enabled, this file includes each remote proxy's gap traffic too, merged in the same way as a
  scenario's local and remote traffic.

Files are also registered as **Serenity evidence artifacts** and appear in the HTML test report alongside screenshots and other evidence.

### File Format

The files are [pcapng](https://www.ietf.org/archive/id/draft-ietf-opsawg-pcapng-05.html), written by
Tiger itself with nanosecond timestamps. Every interface — and, in a merged file, every interface of
every remote proxy — is an interface of its own in the file, named after where it came from (for
example `proxy-1 / eth0`). Wireshark therefore shows which interface or proxy each packet was
captured on, and interfaces with different link types, such as a BSD loopback next to Ethernet,
share one file without any of their packets being rewritten. Every packet carries the time it was
captured (for a remote one: minus the clock offset to that proxy), never the time of the merge.

## Wireshark Inspection

### Opening a File

1. Launch Wireshark
2. `File → Open` → navigate to `target/evidences/`
3. Select a `.pcapng` file
4. Click "Open"

### Typical Workflows

**Inspect a specific request**:
1. Filter by port: `tcp.port == 8080` (proxy port)
2. Find the GET/POST request by looking for HTTP protocol entries
3. Click on the packet → inspect headers, body, timing

**Decode encrypted traffic** (TLS):
1. If you have the private key, configure it in `Wireshark → Preferences → Protocols → TLS`
2. Wireshark will decrypt and show plaintext HTTP inside TLS
3. Otherwise, observe the encrypted handshake, certificate exchange, etc.

**Search for errors**:
1. Filter by status: `http.response.code >= 400`
2. Or by TCP flags: `tcp.flags.reset == 1` (connection resets)

## Troubleshooting

### "WARN … pcap capture disabled: …" in logs

The native pcap library is not available or the user lacks permissions.

**Windows:**
- Verify Npcap is installed: `Settings → Apps → Npcap`
- If missing, download from [npcap.com](https://npcap.com/) and install with WinPcap-compatible mode enabled
- Restart the test suite

**Linux:**
- Install libpcap: `sudo apt-get install libpcap-dev` (Debian/Ubuntu) or equivalent
- Grant capabilities (non-root): `sudo setcap cap_net_raw,cap_net_admin=eip $(readlink -f $(which java))`
- Or run tests with `sudo`

**macOS:**
- Install Wireshark (includes ChmodBPF): https://www.wireshark.org/download/
- Or manually: `sudo dseditgroup -o edit -a $(whoami) -t user access_bpf` and log out/in

### No `.pcapng` files in `target/evidences/`

- Check that `lib.pcapCapture.enabled: true` is set in `tiger.yaml`
- Verify Tiger discovered proxy ports: look for log line "Discovered X proxy ports for pcap capture"
- If native library is missing, a WARN is logged (see above)
- **If `startSuspended: true`**: Files are only created when `PCAP capture resumes` is called. Scenarios that never resume produce no files (this is intentional—no traffic was captured). Check your BDD steps.

### `.pcapng` files are very large or empty

- **Large files**: Reduce `snaplenKb` (fewer bytes per packet) or `bufferSizeKb` (smaller buffer)
- **Empty files**: Ensure traffic actually flows through the proxy port (filter may be too restrictive, or no traffic captured in testcase timespan)

### Parallel Test Execution

Both local and distributed capture — including suspend/resume — are safe under parallel test
execution: each scenario gets its own capture file, so packets are never misattributed to the wrong
scenario, and one scenario suspending capture never affects another running concurrently.

The one caveat: if two scenarios run concurrently and both capture on the same local interface(s),
both receive the *same* packets in their own files — there's no reliable way to tell which scenario
a given packet belongs to without per-connection tracking. This is usually harmless, but if that
duplication matters for your analysis, avoid sharing interfaces across parallel scenarios or run
sequentially.

## Limitations

### Local Capture
| Limitation | Reason | Workaround |
|-----------|--------|-----------|
| Local host only | Capture runs on local interfaces — remote proxies invisible | Use Distributed Capture for multi-host, or Serenity RBel evidence |
| Overlapping scenarios can share packets | Two scenarios capturing on the same interface(s) at the same time both receive the same packets — see [Parallel Test Execution](#parallel-test-execution) | Usually harmless; avoid sharing interfaces across parallel scenarios if it matters |

### Distributed Capture
| Limitation | Reason | Workaround |
|-----------|--------|-----------|
| Network latency | Remote file download and merging happens synchronously at scenario finish, can extend scenario teardown | Increase `remoteDownloadTimeoutSeconds` or reduce PCAP file sizes via `snaplenKb` in that proxy's `pcapCapture` block (see [Configuring Capture per Remote Proxy](#configuring-capture-per-remote-proxy)) |
| Clock sync required | Requires NTP or similar clock sync across hosts for accurate timestamp alignment | Ensure all test hosts run NTP/Chrony; remote clock offset compensation handles minor drifts |

## Performance Impact

- **Capture overhead**: ~1–2% depending on traffic volume and JVM GC behavior
- **Disk usage**: ~10–100 MB per hour depending on traffic intensity and snaplen
- **Memory**: ~16 MB kernel ring buffer (configurable); application heap impact is minimal (streaming, no accumulation)

## References

- **Configuration options**: `TigerLibConfig#getPcapCapture()` → `TigerPcapCaptureConfig`
- **ADR** (architectural decisions): `doc/adr/020_pcap_capture.md`
- **Troubleshooting**: See [FAQ.md](../../FAQ.md#fo04)
