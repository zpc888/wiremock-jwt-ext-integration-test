# JWT extension integration stack

This Compose project builds `zpc888/wiremock-jwt-extension` branch
`feature/config-signing-keys` in a Java 17 container and mounts its generated
extension JAR into two WireMock 3.13.1 containers.

The remaining containers use Java 21:

- `key-generator` creates an RSA private key and its matching JKS truststore
  under `containers-config/pre-defined-keys/` when either artifact is absent.
- `service-jwks` verifies inbound tokens from WireMock's JWKS endpoint and
  obtains outbound tokens from the fixed-key signing WireMock.
- `service-keystore` verifies inbound tokens using the mounted JKS truststore
  and obtains outbound tokens from the JWKS WireMock.
- `verifier` calls both public APIs and confirms the signing WireMock publishes
  the fixed key ID `compose-signing-key-1`.

Start the stack with the wrapper. It passes your current numeric UID and GID to
the two artifact-producing containers, so their bind-mounted JAR, private key,
certificate, and truststore are owned by your host user rather than `root`:

```bash
./compose.sh up --build
```

The public endpoints default to:

| Service | Host endpoint |
| --- | --- |
| `service-jwks` | `http://localhost:8080/api/public` |
| `service-keystore` | `http://localhost:8082/api/public` |
| `wiremock-jwks` | `http://localhost:9090` |
| `wiremock-signing` | `http://localhost:9091` |

Override occupied WireMock host ports without changing the Compose file:

```bash
WIREMOCK_JWKS_HOST_PORT=19090 WIREMOCK_SIGNING_HOST_PORT=19091 ./compose.sh up --build
```

Generated extension JARs, private keys, certificates, and truststores are
ignored by Git. Override the local demo defaults with `OAUTH_CLIENT_SECRET`,
`TRUSTSTORE_PASSWORD`, and `TRUSTSTORE_ALIAS`.

Do not invoke `docker compose` directly: `compose.yml` requires `HOST_UID` and
`HOST_GID` to protect bind-mounted artifact ownership. The wrapper sets both
from `id -u` and `id -g`.
