# PCAP Capture Configuration and Usage

## Overview

Tiger automatically captures HTTP/HTTPS proxy traffic in-process using `pcap4j` and `libpcap`. Each test scenario generates a `.pcapng` (Next Generation Pcap) file suitable for inspection in Wireshark, containing the complete request/response exchange including TLS handshakes, redirects, retries, and any protocol-level details.

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

## Configuration Reference

All settings live under `lib.pcapCapture.*`:

| Setting | Type | Default | Purpose |
|---------|------|---------|---------|
| `enabled` | boolean | `false` | Master switch; without this, no capture threads start. |
| `startSuspended` | boolean | `false` | Start each scenario with capture suspended (`true`) or enabled (`false`). Use with BDD steps to control when recording begins. |
| `splitByTestcase` | boolean | `true` | One file per scenario (`true`) vs. one file for the entire suite (`false`). |
| `snaplenKb` | integer | `64` | Per-packet bytes copied to userspace (1 KB = 1024 B). Local loopback MTU is typically 64–65 KB; reduce if disk space is tight. |
| `bufferSizeKb` | integer | `16384` | Kernel ring buffer size in KB. Larger absorbs JVM/GC pauses; smaller risks silent packet loss. |
| `filename` | string | `${scenarioId}.pcapng` | Filename template for the per-testcase file when `splitByTestcase=true`; `${scenarioId}` is substituted with the real scenario id. Ignored when `splitByTestcase=false` — the suite-wide file always gets a generic, run-unique name instead (see [File Locations and Naming](#file-locations-and-naming)). |
| `bpfFilter` | string | *(none)* | Manual BPF filter expression (e.g. `"tcp port 8080 or tcp port 9090"`), passed verbatim to the capture. When set, this replaces automatic port discovery entirely and is never widened afterwards — use it when the automatic discovery misses traffic you need. |

### Example Configuration

```yaml
lib:
  pcapCapture:
    enabled: true                          # Enable capture
    splitByTestcase: true                  # One file per scenario (default)
    snaplenKb: 64                          # Capture full 64 KB frames (loopback MTU)
    bufferSizeKb: 16384                    # 16 MB kernel buffer
    filename: ${scenarioId}.pcapng         # Per-testcase filename template (splitByTestcase: true)
```

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

- **Suspend/resume state is per-scenario**: Each new scenario starts with the baseline state defined by `startSuspended` config
- **Lazy file creation**: `.pcapng` files are only created if traffic is captured; paused scenarios produce no file
- **Baseline restoration**: At the end of each scenario, the capture state reverts to the configured baseline (enabled or suspended)
- **Baseline state**: Set via `startSuspended` config (see Configuration Reference below)

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

## What Gets Captured

Tiger automatically discovers all Tiger Proxy instances in your test environment and applies a BPF filter to capture traffic on:

1. **Proxy listening port** (e.g., 8080) — client ↔ proxy traffic
2. **Proxy admin port** (e.g., 9000) — admin API calls, metrics, health checks
3. **Route target ports** — all backend/upstream server ports defined in proxy routes

**Example**: If your proxy routes `/api` to `localhost:3000` and `/health` to `localhost:5000`, capture includes traffic on ports 8080 (proxy), 9000 (admin), 3000, and 5000.

**Proxies started mid-run**: a `TigerProxyServer` started after capture begins (e.g. via `TGR start server`) is picked up automatically — the filter widens (never narrows) whenever the test environment reports a server change, and again at every scenario boundary as a catch-all. If you need full control over the filter instead, set `bpfFilter` to bypass automatic discovery entirely.

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
- **Suite-wide file** (if `splitByTestcase=false`): `suite_<epochMillis>.pcapng` for the whole
  run — a generic, run-unique name; the `filename` template is not used here (no per-scenario
  context exists yet at suite start).
- **Pre-first-scenario and between-scenario files** (if `splitByTestcase=true`): traffic before
  the first scenario, and between any two scenarios, lands in its own `suite_<epochMillis>.pcapng`
  file, separate from every scenario's own file. If nothing was captured in a given window, no
  file appears for it.

Files are also registered as **Serenity evidence artifacts** and appear in the HTML test report alongside screenshots and other evidence.

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

### Parallel test execution loses packet boundaries

Tiger v1 uses wall-clock timestamps to slice packets per scenario. **Parallel test execution is not supported** — packets from concurrent scenarios may be misattributed. Run tests sequentially or use v2 (distributed capture with explicit testcase markers).

## Limitations (v1)

| Limitation | Reason | Workaround |
|-----------|--------|-----------|
| Local host only | Capture runs on the host (loopback by default; `interfaceNames` can select a specific host NIC, e.g. `["eth0"]`, or `["*"]` for the first non-loopback interface) — Docker-internal or remote proxies are invisible either way. | Use Serenity RBel evidence for remote proxy traffic (full message payload already captured). v2 (multi-host capture) planned. |
| Sequential execution required | Packet-to-scenario association relies on timestamps. | Run tests with `cucumber.plugin.parallel.workers=1` or similar. |
| Snaplen is per-packet, not per-connection | If payload exceeds snaplen, it's truncated. | Increase `snaplenKb` or accept truncated packets in Wireshark. |

## Performance Impact

- **Capture overhead**: ~1–2% depending on traffic volume and JVM GC behavior
- **Disk usage**: ~10–100 MB per hour depending on traffic intensity and snaplen
- **Memory**: ~16 MB kernel ring buffer (configurable); application heap impact is minimal (streaming, no accumulation)

## References

- **Configuration options**: `TigerLibConfig#getPcapCapture()` → `TigerPcapCaptureConfig`
- **ADR** (architectural decisions): `doc/adr/020_pcap_capture.md`
- **Troubleshooting**: See [FAQ.md](../../FAQ.md#fo04)
