# Errors (status: draft, phase 0)

## GraphQL errors (`errors[].extensions.code`)
Unexpected or protocol-level failures. Messages are safe for users; no stack traces, SQL, or internal IDs.

| Code | Meaning | Client behaviour |
| --- | --- | --- |
| `UNAUTHENTICATED` | No valid session/token | Go to sign-in, keep the current route to return to |
| `FORBIDDEN` | Authenticated but not permitted | Show "You don't have access"; never reveal whether the object exists |
| `NOT_FOUND` | Object missing or not visible to the caller | Show not-found state |
| `BAD_REQUEST` | Malformed input that schema validation did not catch | Show generic error; log for developers |
| `CONFLICT` | Optimistic version mismatch | Reload the object and ask the user to re-apply |
| `RATE_LIMITED` | Too many requests; `extensions.retryAfterSeconds` | Back off and retry |
| `QUERY_TOO_COMPLEX` | Depth/complexity/cost limit exceeded | Developer error |
| `TIMEOUT` | Server-side deadline exceeded | Retry once for reads |
| `UNAVAILABLE` | Dependency down | Offline/retry state |
| `INTERNAL` | Anything else | Generic error with retry; correlation ID in `extensions.correlationId` |

## Business failures (`UserError` in mutation payloads)
`UserError { code: String!, message: String!, field: [String!] }` — `field` is the path in the input (e.g. `["input","address","phone"]`).

Initial codes (backend adds more per phase, here):
`VALIDATION_FAILED`, `EMAIL_TAKEN`, `INVALID_CREDENTIALS` (same message whether email or password is wrong), `ACCOUNT_INACTIVE`, `TOKEN_INVALID_OR_EXPIRED`, `PASSWORD_TOO_WEAK`, `ROLE_CYCLE`, `OUT_OF_STOCK`, `INSUFFICIENT_STOCK`, `PRICE_CHANGED`, `PROMO_INVALID`, `PROMO_EXPIRED`, `PROMO_MIN_NOT_MET`, `PROMO_USAGE_LIMIT`, `POINTS_INSUFFICIENT`, `INVALID_STATUS_TRANSITION`, `PAYMENT_DECLINED`, `DUPLICATE_REQUEST`, `UPLOAD_REJECTED`.
