# Tiger Standalone Proxy

This module repackages the [tiger-proxy](../tiger-proxy/README.md) as a self-contained,
runnable Spring Boot JAR and as a **Docker image**.

For a full description of all configuration options, `docker run` examples, environment
variables and runtime behaviour please refer to the
[tiger-proxy README](../tiger-proxy/README.md) and the
[Tiger user manual](https://gematik.github.io/app-Tiger/Tiger-User-Manual.html).

---

## What this module produces

| Artifact                                      | Description |
|-----------------------------------------------|---|
| `tiger-standalone-proxy-<version>.jar`        | Executable fat JAR (Spring Boot repackaged) |
| `tiger-standalone-proxy-<version>-docker.jar` | Variant used inside the Docker image |
| `gematik1/tiger-proxy-image:<version>` | Docker image (only when `-Pdocker` is active) |

## Building

```bash
# JAR only
mvn -pl tiger-standalone-proxy -am package -DskipTests

# JAR + Docker image
mvn -Pdocker -pl tiger-standalone-proxy -am package -DskipTests

# Just rebuild the Docker image (JARs in target/ must already exist)
mvn -Pdocker -pl tiger-standalone-proxy docker:build

# Override image name / tag / commit hash
mvn -Pdocker -pl tiger-standalone-proxy package -DskipTests \
    -Ddocker.image.name=my-org/tiger-proxy-image \
    -Ddocker.image.tag=dev \
    -Ddocker.commit.hash=$(git rev-parse --short HEAD)
```

## Docker image

The image is described by [`Dockerfile`](Dockerfile) in this module.

### Quick start

```bash
docker run --rm \
  -p 8080:8080 \
  -p 9090:9090 \
  gematik1/tiger-proxy-image:latest
```

* `8080` — admin / actuator / WebUI / REST API
* `9090` — the actual forward proxy port (`TIGERPROXY_PROXYPORT`)

For more examples (mounting config files, setting ports via environment variables,
persisting traffic dumps, read-only containers, …) see the
[tiger-proxy README](../tiger-proxy/README.md#docker-image).
