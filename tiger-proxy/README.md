# Tiger Proxy

The Tiger Proxy is a flexible HTTP/HTTPS routing proxy that records, parses and exposes the
traffic it observes (RBel logs, WebUI, REST API). It is the heart of the Tiger Test Platform
and can also be used standalone.

For a detailed explanation of all configuration options see the
[Tiger user manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.html).

---

## Docker image

This module is also published as a Docker image. The sections below show the most common
ways to run it. They are intentionally kept short — for everything beyond the basics please
refer to the user manual linked above.

### Building the image locally

The image is described by [`Dockerfile`](Dockerfile) in this module. You can either invoke
`docker build` yourself, or build it through Maven via the `docker` profile (which uses
the [fabric8 `docker-maven-plugin`](https://dmp.fabric8.io/) under the hood):

```bash
# Build jars + image in one go
mvn -Pdocker -pl tiger-proxy -am package

# Just the image (jars in target/ must already exist)
mvn -Pdocker -pl tiger-proxy docker:build

# Override name/tag/commit hash if needed
mvn -Pdocker -pl tiger-proxy package \
    -Ddocker.image.name=my-org/tiger-proxy \
    -Ddocker.image.tag=dev \
    -Ddocker.commit.hash=$(git rev-parse --short HEAD)
```

The profile is opt-in so plain `mvn package` still works on machines without a Docker
daemon. CI builds keep using the existing `dockerBuild(...)` Jenkins shared-library
function — both paths consume the same `Dockerfile`, so the resulting images are
identical.

### Quick start

```bash
docker run --rm \
  -p 8080:8080 \
  -p 9090:9090 \
  gematik1/tiger-proxy:latest
```

* `8080` &mdash; admin / actuator / WebUI / REST API
* `9090` &mdash; the actual forward proxy port (`TIGERPROXY_PROXYPORT`)

### How do I mount an `application.yaml` into the container?

Place your configuration on the host (e.g. `./application.yaml`) and bind-mount it into the
container's working directory `/app`. Spring Boot picks it up automatically:

```bash
docker run --rm \
  -p 8080:8080 -p 9090:9090 \
  -v "$(pwd)/application.yaml:/app/application.yaml:ro" \
  gematik1/tiger-proxy:latest
```

A minimal `application.yaml` could look like this:

```yaml
tigerProxy:
  proxyPort: 9090
  adminPort: 8080
  proxyRoutes:
    - from: /
      to: https://my-backend.example.com
  modifications:
    - condition: "isResponse"
      targetElement: "$.header.x-traced-by"
      replaceWith: "tiger-proxy"
```

If your file lives elsewhere, point Spring at it explicitly:

```bash
docker run --rm \
  -p 8080:8080 -p 9090:9090 \
  -v "$(pwd)/my-config.yaml:/config/my-config.yaml:ro" \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=/config/my-config.yaml \
  gematik1/tiger-proxy:latest
```

### How do I set ports without YAML?

Every Tiger / Spring Boot property can be overridden with an environment variable. The most
common ones are:

```bash
docker run --rm \
  -e TIGERPROXY_ADMINPORT=8081 \
  -e TIGERPROXY_PROXYPORT=9091 \
  -p 8081:8081 \
  -p 9091:9091 \
  gematik1/tiger-proxy:latest
```

The Spring Boot configuration root is `tiger-proxy` (kebab-case, see
`ApplicationConfiguration`). Spring's environment-variable binding rule is
"uppercase, replace `.` with `_`, and **drop hyphens entirely**", so the prefix
`tiger-proxy` becomes `TIGERPROXY_` — *not* `TIGER_PROXY_` (which would address a
different, non-existent property `tiger.proxy.*`). Common examples:

| YAML / property                       | Environment variable                  |
|---------------------------------------|---------------------------------------|
| `tiger-proxy.proxyPort`               | `TIGERPROXY_PROXYPORT`                |
| `tiger-proxy.adminPort`               | `TIGERPROXY_ADMINPORT`                |
| `tiger-proxy.tls.masterSecretsFile`   | `TIGERPROXY_TLS_MASTERSECRETSFILE`    |

If you'd rather pass a structured override, `SPRING_APPLICATION_JSON` always works
and bypasses any binding subtleties:

```bash
docker run --rm \
  -p 8080:8080 -p 9090:9090 \
  -e SPRING_APPLICATION_JSON='{"tiger-proxy":{"proxyPort":9090}}' \
  gematik1/tiger-proxy:latest
```

### Running as a non-root user

The image already runs as UID/GID `10000:10000` by default. If you mount files into the
container, make sure they are readable by that UID (or override `USERID` / `GROUPID` at
build time).

### Persisting runtime artifacts (master secrets, traffic dumps, ...)

The container's working directory `/app` is owned by the runtime user and is
writable. Relative paths in the configuration are resolved against `/app`, so
the simplest way to capture runtime artifacts is to bind-mount a host
directory onto a subdirectory of `/app` (we recommend `/app/output`) and to
point the corresponding configuration at it:

```bash
docker run --rm \
  -p 8080:8080 -p 9090:9090 \
  -v "$(pwd)/tiger-data:/app/output" \
  -e TIGERPROXY_TLS_MASTERSECRETSFILE=output/master-secrets.txt \
  gematik1/tiger-proxy:latest
```

Absolute paths (`/app/output/master-secrets.txt`) work just as well.

> **Security note.** Because `/app` is writable for the runtime user, the
> running proxy can in principle overwrite its own `app.jar` / `agent.jar`.
> The jars are shipped read-only (mode `0444`), so an explicit `chmod` is
> required to replace them — that is a speed-bump, not a strong protection.
> Operators who need stronger guarantees should run the container with
> `--read-only` and a writable `tmpfs` (or, in Kubernetes,
> `readOnlyRootFilesystem: true` together with an `emptyDir` volume) mounted
> at the path they actually want to write to, e.g.:
>
> ```bash
> docker run --rm --read-only \
>   --tmpfs /app/output:rw,uid=10000,gid=10000 \
>   -p 8080:8080 -p 9090:9090 \
>   -e TIGERPROXY_TLS_MASTERSECRETSFILE=output/master-secrets.txt \
>   gematik1/tiger-proxy:latest
> ```

### Health-check

The image ships with a conservative `HEALTHCHECK` that calls
`http://localhost:8080/actuator/health` (start-period 20 s, interval 30 s, 3 retries).
Because each probe results in real traffic in the proxy, please do not lower these values
in derived images.

### Where do I find more details?

* Full documentation: [Tiger user manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.html)
* Source / issue tracker: [github.com/gematik/app-Tiger](https://github.com/gematik/app-Tiger)
* Release notes: [`ReleaseNotes.md`](../ReleaseNotes.md)
