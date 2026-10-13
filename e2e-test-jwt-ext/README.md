# JWT extension end-to-end test

This Compose project builds `zpc888/wiremock-jwt-extension` branch
`feature/config-signing-keys` in a Java 17 container and mounts its generated
extension JAR into three WireMock 3.13.1 containers.

The E2E project is a Java 21 JUnit integration test. Gradle starts the
dependency stack before executing the test and tears it down afterward:

- `key-generator` creates an RSA private key and its matching JKS truststore
  under `containers-config/pre-defined-keys/` when either artifact is absent.
- `service-jwks-client` verifies inbound tokens from WireMock's JWKS endpoint
  and obtains outbound tokens from the fixed-key signing WireMock.
- `service-keystore-client` verifies inbound tokens using the mounted JKS
  truststore and obtains outbound tokens from the JWKS WireMock.
- The host-side test calls both public APIs and compares their stable JSON
  maps with exact expected maps. It also obtains the HS256 token from
  `wiremock-jwt-header`, validates its signature with the WireMock admin
  secret, and validates its header, payload, and time claims.

Start the stack with the wrapper. It passes your current numeric UID and GID to
the two artifact-producing containers, so their bind-mounted JAR, private key,
certificate, and truststore are owned by your host user rather than `root`:

```bash
./gradlew :e2e-test-jwt-ext:test
```

Run the command from the repository root. The root `./gradlew test` also runs
this E2E test after `:service-app:test`. The E2E Gradle lifecycle starts
Compose through `compose.sh`, executes the host-side integration test, and
always tears the stack down. Use `./compose.sh` only for interactive debugging.

The public endpoints default to:

| Service | Host endpoint |
| --- | --- |
| `service-jwks-client` | `http://localhost:8181/api/public` |
| `service-keystore-client` | `http://localhost:8182/api/public` |
| `wiremock-jwks` | `http://localhost:8185` |
| `wiremock-keystore` | `http://localhost:8186` |
| `wiremock-jwt-header` | `http://localhost:8187` |

Override occupied WireMock host ports without changing the Compose file:

```bash
WIREMOCK_JWKS_HOST_PORT=19185 \
WIREMOCK_SIGNING_HOST_PORT=19186 \
WIREMOCK_JWT_HEADER_HOST_PORT=19187 \
SERVICE_JWKS_HOST_PORT=19181 \
SERVICE_KEYSTORE_HOST_PORT=19182 \
./gradlew :e2e-test-jwt-ext:test
```

Generated extension JARs, private keys, certificates, and truststores are
ignored by Git. Override the local demo defaults with `OAUTH_CLIENT_SECRET`,
`TRUSTSTORE_PASSWORD`, and `TRUSTSTORE_ALIAS`.

Do not invoke `docker compose` directly: `compose.yml` requires `HOST_UID` and
`HOST_GID` to protect bind-mounted artifact ownership. The Gradle lifecycle
uses the wrapper, which sets both from `id -u` and `id -g`.
