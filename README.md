# Configurable peer service

`service-app` is one Spring Boot artifact deployed twice with different runtime configuration. Each instance exposes the same APIs:

| API | Access | Behavior |
| --- | --- | --- |
| `GET /api/public` | Public | Obtains an OAuth client-credentials token, calls the configured peer's private API, and returns its application name with the peer response. |
| `GET /api/private` | Bearer JWT | Verifies the JWT signature and configured header, claim, issuer, audience, and scope rules. |

The service never decides whether it is “app1” or “app2” in code. `spring.application.name`, `service.peer.*`, OAuth settings, and JWT verification settings determine the role at deployment time.

## Deploy twice

The checked-in `jwks` and `keystore` profiles are non-secret verification examples:

```bash
./gradlew :service-app:bootRun --args='--spring.profiles.active=jwks'
./gradlew :service-app:bootRun --args='--spring.profiles.active=keystore --server.port=8082 --management.server.port=8083'
```

Override all actual deployment values with environment variables or standard Spring external configuration. Do not place client secrets or keystore passwords in versioned YAML.

| Property | Environment variable | Purpose |
| --- | --- | --- |
| `spring.application.name` | `SPRING_APPLICATION_NAME` | Required local deployment identity. |
| `service.peer.base-url` | `SERVICE_PEER_BASE_URL` | Required peer base URL. The service calls `/api/private`. |
| `service.peer.required-scopes` | `SERVICE_PEER_REQUIRED_SCOPES` | Scopes requested from OAuth and expected by the peer. |
| `service.oauth.token-url` | `SERVICE_OAUTH_TOKEN_URL` | OAuth client-credentials endpoint. |
| `service.oauth.client-id` | `SERVICE_OAUTH_CLIENT_ID` | OAuth Basic-auth user. |
| `service.oauth.client-secret` | `SERVICE_OAUTH_CLIENT_SECRET` | OAuth Basic-auth password; supply from a secret store. |
| `service.jwt.verification-mode` | `SERVICE_JWT_VERIFICATION_MODE` | Exactly one of `JWKS` or `KEYSTORE`. |
| `service.jwt.issuer` | `SERVICE_JWT_ISSUER` | Required exact `iss` value. |
| `service.jwt.audiences` | `SERVICE_JWT_AUDIENCES` | Required audience allow-list; one value must appear in `aud`. |
| `service.jwt.required-scopes` | `SERVICE_JWT_REQUIRED_SCOPES` | All scopes required by this instance's private API. |
| `service.jwt.scope-claim` | `SERVICE_JWT_SCOPE_CLAIM` | Scope claim name, default `scope`; accepts a space-separated string or a JSON string array. |
| `service.jwt.header-claims.*` | — | Required scalar JWT header fields, such as `typ=JWT`. RS256 is always enforced. |
| `service.jwt.claim-equals.*` | — | Required scalar payload values by JSON Pointer, such as `/client_id=caller-a`. |
| `service.jwt.claim-contains.*` | — | Required array member by JSON Pointer, such as `/tenants=blue`. |
| `service.connect-timeout` | `SERVICE_CONNECT_TIMEOUT` | Connect timeout for token, peer, and JWKS requests; default `2s`. |
| `service.token-response-timeout` | `SERVICE_TOKEN_RESPONSE_TIMEOUT` | Token endpoint response timeout; default `5s`. |
| `service.peer-response-timeout` | `SERVICE_PEER_RESPONSE_TIMEOUT` | Peer private-API response timeout; default `10s`. |
| `service.jwks-response-timeout` | `SERVICE_JWKS_RESPONSE_TIMEOUT` | JWKS response timeout; default `5s`. |

## JWT verification

All private JWTs must use `RS256`, contain `exp`, have the configured issuer and at least one configured audience, and satisfy every configured header, payload, and scope rule. `nbf` is validated when present with a configurable 60-second default clock skew.

### JWKS mode

```yaml
service:
  jwt:
    verification-mode: JWKS
    jwks:
      jwk-set-url: https://issuer.example/.well-known/jwks.json
```

Keys are fetched lazily on the first JWT validation and refreshed by the underlying key resolver for unknown key IDs. The application starts without contacting the issuer. JWKS requests use the configured connect and JWKS response timeouts.

### Keystore mode

```yaml
service:
  jwt:
    verification-mode: KEYSTORE
    keystore:
      location: file:/var/run/secrets/jwt-verifier.p12
      type: PKCS12
      password: ${SERVICE_JWT_KEYSTORE_PASSWORD}
      alias: jwt-verifier
```

The selected alias must resolve to an RSA certificate. JKS and PKCS12 are supported. The `keystore` profile uses `file:${java.io.tmpdir}/service-app-jwt-verifier.p12` by default; its test fixture generates an RSA key and certificate dynamically and writes the resulting PKCS12 keystore there. Override `SERVICE_JWT_KEYSTORE_LOCATION`, password, type, and alias for deployed environments.

## Runtime behavior

- OAuth requests use `application/x-www-form-urlencoded` client credentials and HTTP Basic authentication.
- Tokens require `access_token` and positive `expires_in`; they are cached per service with single-flight refresh 30 seconds before expiry. Short-lived tokens are used once.
- No call retries occur. Token/peer transport failures and peer authorization failures become sanitized RFC 9457 `502` responses with `code: peer_unavailable`.
- JWT failures return RFC 9457 `401 invalid_token`; insufficient scope returns `403 insufficient_scope`.
- Peer calls carry the caller's `spring.application.name` in `X-Caller-App-Name`. Private APIs reject matching caller and callee names with `403 self_private_call`.
- `X-Request-Id` is accepted only when ASCII-safe and 1–128 characters; otherwise a UUID is generated. It is forwarded to the peer and returned in response headers and problem details.
- HTTP endpoint URLs emit a startup warning. Deploy behind a service mesh or sidecar that provides transport encryption.

Management health and metrics use the configurable management port/address. Health exposes status only; bind the management address to a private interface and enforce metrics access with infrastructure network policy.

## Verify

```bash
./gradlew test
```

Tests run the artifact as two separately configured instances, issue WireMock-backed OAuth/JWKS tokens, exercise scopes, signatures, custom claims, token caching, and PKCS12 certificate verification.
