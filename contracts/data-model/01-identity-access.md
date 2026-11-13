# Phase 1 — Identity, access, organization (status: review)

Modules: `organization`, `identity`, `authorization`, `audit`. Conventions and standard columns: [00-conventions.md](00-conventions.md). GraphQL: [../graphql/01-identity.graphqls](../graphql/01-identity.graphqls).

Columns: **Req** `Y` = NOT NULL; **PII** = personal data (erasure/anonymisation, restricted access, never logged).

## organization (implemented in phase 0, migration `V0001`)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `name` | text (1–200) | Y | | legal/brand name ("Aurelle") |
| `slug` | citext unique, `^[a-z0-9]+(-[a-z0-9]+)*$` | Y | | stable key (`aurelle`) |
| `default_currency` | char(3) ISO 4217 | Y | | |
| `default_locale` | text BCP 47 | Y | | e.g. `en`, `en-RW` |
| `time_zone` | text IANA | Y | | reports, "today" |
| `support_email` / `support_phone` | text / E.164 | - | | footer, e-mails, `shopSettings` |
| `tax_id` | text ≤ 64 | - | | invoices (D11) |
| `logo_media_id` | uuid → `media` | - | | FK added in phase 2 |

Production: one row `aurelle` (D15: single organization until multi-tenancy is decided). Seed: see [Seed data](#seed-data-owner-rule--4-rows-per-table-profiles-devtesttestingstaging-only).

## location
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `organization_id` | uuid → organization | Y | | scope |
| `name` | text (1–120) | Y | | "Online store", "Kigali studio" |
| `kind` | text check (`STORE`,`WAREHOUSE`,`ONLINE`) | Y | | stock and staff scope |
| `country_code` | char(2) ISO 3166-1 | - | | address, generic levels as in `address` (phase 4) |
| `admin_area_1..4`, `locality`, `street_line_1/2`, `postal_code`, `landmark` | text | - | | pick-up and returns address |
| `latitude` / `longitude` / `location_accuracy_m` | numeric(9,6) / numeric(9,6) / int | - | | map pin for pick-up |
| `phone_e164` | text | - | | store phone |
| `time_zone` | text | - | | overrides organization |
| `active` | boolean default true | Y | | |
| unique | (`organization_id`, `name`) | | | |

Production: one `ONLINE` location "Online store" for `aurelle` (migration). Seed: see [Seed data](#seed-data-owner-rule--4-rows-per-table-profiles-devtesttestingstaging-only).

## user_account
One row per person who can sign in. Guests who buy without an account have **no** `user_account` (only a `customer_profile`, phase 4).

| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `organization_id` | uuid → organization | Y | | tenant scope |
| `kind` | text check (`CUSTOMER`,`STAFF`) | Y | | separates self-service from staff policies |
| `email` | citext, unique per organization where not null | - | PII | sign-in, receipts |
| `email_verified_at` | timestamptz | - | | |
| `phone_e164` | text `^\+[1-9][0-9]{6,14}$`, unique per organization where not null | - | PII | sign-in where e-mail is rare |
| `phone_verified_at` | timestamptz | - | | needs an SMS provider (D28) |
| `password_hash` | text | - | secret | Argon2id (`$argon2id$…`); null only for `INVITED` staff |
| `password_changed_at` | timestamptz | - | | invalidates older sessions |
| `status` | text check (`ACTIVE`,`INACTIVE`,`LOCKED`,`INVITED`) | Y | | prototype `active` flag |
| `failed_sign_in_count` | int default 0, ≥ 0 | Y | | brute-force protection |
| `locked_until` | timestamptz | - | | automatic unlock |
| `last_sign_in_at` | timestamptz | - | | team screen |
| `preferred_locale` | text BCP 47 | - | | |
| `preferred_currency` | char(3) | - | | display only; prices stay in shop currency |
| `theme_preference` | text check (`SYSTEM`,`DARK`,`LIGHT`) default `SYSTEM` | Y | | sun/moon toggle synced across devices |
| `marketing_consent_at` / `marketing_consent_withdrawn_at` | timestamptz | - | | consent evidence |
| `terms_accepted_at` / `terms_version` | timestamptz / text | - | | required for customers at sign-up |
| `erased_at` | timestamptz | - | | anonymisation marker (PII columns nulled) |
| CHECK | `email IS NOT NULL OR phone_e164 IS NOT NULL OR erased_at IS NOT NULL` | | | |
| CHECK | `status = 'INVITED' OR password_hash IS NOT NULL OR erased_at IS NOT NULL` | | | |

Standard columns `id`, `created_at`, `updated_at`, `version`. Never hard-deleted; erasure nulls PII.

## customer_profile (1:1 with a CUSTOMER account; phase 4 also creates guest profiles)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `organization_id` | uuid | Y | | scope |
| `user_account_id` | uuid unique → user_account | - | | null for guest buyers (phase 4) |
| `display_name` | text (1–120) | Y | PII | "Amina Okoye" |
| `given_name` / `family_name` | text | - | PII | greetings, invoices |
| `avatar_media_id` | uuid → media | - | PII | top-right profile photo (FK in phase 2) |
| `birth_date` | date | - | PII | opt-in birthday treat |
| `shop_for` | text[] of `Audience` values (`MEN`,`WOMEN`,`BOYS`,`GIRLS`,`KIDS_UNISEX`,`UNISEX`) default `{}` | Y | | default audience tab, personalisation |
| `favourite_category_ids` | uuid[] default `{}` | Y | | personalisation (phase 2 categories) |
| `acquisition_source` | text check (`INSTAGRAM`,`TIKTOK`,`FACEBOOK`,`FRIEND`,`SEARCH`,`STORE`,`OTHER`) | - | | "How did you hear about us?" (optional) |
| `referral_code` | text unique | - | | invite-a-friend (phase 7) |
| `is_guest` | boolean default false | Y | | prototype `accountGuest` |
| `staff_note` | text ≤ 2000 | - | PII | staff-only CRM note; never returned to the customer |

## customer_fit_profile (opt-in "save my sizes"; implemented with the size finder in phase 2)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `customer_profile_id` | uuid → customer_profile | Y | | several per customer (self, child) |
| `label` | text (1–40) | Y | | "Me", "Kofi (son)" |
| `audience` | text (`Audience`) | Y | | |
| `top_size` / `bottom_waist` / `shoe_size_eu` | text | - | | usual sizes |
| `height_cm` / `weight_kg` / `age_years` | smallint 50–250 / numeric(4,1) 2–300 / smallint 0–120 | - | PII (sensitive) | size finder; stored only with consent |
| `fit_preference` | text check (`SLIM`,`REGULAR`,`RELAXED`) | - | | |
| `consent_at` | timestamptz | Y | | explicit consent to store body measurements |

## staff_profile
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `user_account_id` | uuid unique → user_account | Y | | |
| `display_name` | text (1–120) | Y | PII | |
| `job_title` | text ≤ 80 | - | | label only; access comes from bindings |
| `avatar_media_id` | uuid | - | PII | |
| `default_location_id` | uuid → location | - | | stock and orders default filter |

## Credentials and sessions (decision D5)
### auth_session
One row per signed-in device. Tokens are opaque random 256-bit values; only SHA-256 hashes are stored.

| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `user_account_id` | uuid → user_account | Y | | |
| `platform` | text check (`WEB`,`ANDROID`) | Y | | |
| `device_label` | text ≤ 80 | - | | "Chrome on Windows"; no IPs |
| `access_token_hash` | bytea unique | Y | | current access token (15 min) |
| `access_expires_at` | timestamptz | Y | | |
| `refresh_token_hash` | bytea unique | Y | | current refresh token (30 days, rotates) |
| `refresh_expires_at` | timestamptz | Y | | |
| `previous_refresh_token_hash` | bytea | - | | reuse detection: presenting it revokes the session |
| `created_at` / `last_seen_at` | timestamptz | Y | | "my devices" list |
| `revoked_at` / `revoked_reason` | timestamptz / text (`SIGN_OUT`,`REVOKED_BY_USER`,`PASSWORD_CHANGED`,`DEACTIVATED`,`REFRESH_REUSE`,`ADMIN`) | - | | |

Expired and revoked sessions are deleted after 30 days (retention).

### auth_token (single-use links)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `user_account_id` | uuid → user_account | Y | | |
| `purpose` | text check (`EMAIL_VERIFY`,`PASSWORD_RESET`,`STAFF_INVITE`) | Y | | |
| `token_hash` | bytea unique | Y | | SHA-256 of the token; the token itself is never stored or logged |
| `expires_at` | timestamptz | Y | | reset 30 min, verify 24 h, invite 72 h |
| `used_at` | timestamptz | - | | single use |
| `requested_ip_hash` | bytea | - | | abuse analysis (salted hash, not the IP) |

Requesting a new token of the same purpose invalidates older unused ones.

## Authorization (data, not enums)
| Table | Fields | Notes |
| --- | --- | --- |
| `permission` | `key` text PK (`^[a-z]+(\.[a-z_]+)+$`), `description`, `group`, `introduced_in` (contract phase) | Seeded and changed only by migration. Keys are stable identifiers. |
| `role` | `organization_id`, `key` (unique per org), `name`, `description`, `kind` (`SYSTEM`,`CUSTOM`), `archived_at`, std columns | System roles cannot be archived or re-keyed |
| `role_permission` | `role_id`, `permission_key`, `effect` (`ALLOW`,`DENY`); PK (`role_id`,`permission_key`) | |
| `role_inheritance` | `child_role_id`, `parent_role_id`; PK both; check child ≠ parent | DAG; cycle check in the use case (recursive CTE) under a row lock on the organization's roles |
| `role_binding` | `user_account_id`, `role_id`, `scope_type` (`ORGANIZATION`,`LOCATION`), `scope_id`, `granted_by`, `granted_at`, `expires_at` (-), `revoked_at` (-), `revoked_by` (-) | Append-style: changes revoke and insert, so history stays |
| `principal_permission_override` | `user_account_id`, `permission_key`, `effect`, `scope_type`, `scope_id`, `granted_by`, `granted_at` | Prototype per-member "page permissions" |

**Evaluation** (explainable, logged on DENY): effective(user, scope) = all ALLOW grants from roles bound at that scope or at the organization scope, plus the roles they inherit from, plus ALLOW overrides; then remove every key with a DENY from any of those sources. Expired or revoked bindings and inactive accounts grant nothing. A deactivated account's sessions are revoked immediately.

**Owner invariant:** there must always be at least one ACTIVE staff account bound to the system `owner` role at ORGANIZATION scope (`LAST_OWNER_REQUIRED`).

### Permission catalogue (seed, phase 1)
Prototype pages → groups. Keys for later phases are seeded now so roles can be set up once; they guard nothing until their phase ships.

| Group (prototype page) | Keys | Owner | Manager | Packer |
| --- | --- | --- | --- | --- |
| Dashboard (`dash`) | `reports.dashboard.read` | ✓ | ✓ | |
| Notifications | `notifications.inbox.read` | ✓ | ✓ | |
| Products | `catalog.product.read`, `catalog.product.write`, `catalog.product.read_cost`, `catalog.category.write`, `media.asset.upload` | ✓ | ✓ | |
| Stock | `inventory.stock.read`, `inventory.stock.adjust` | ✓ | ✓ | ✓ |
| Orders | `orders.order.read`, `orders.order.update_status`, `orders.order.refund` | ✓ | ✓ | read, update_status |
| Promotions | `promotions.promo.read`, `promotions.promo.write` | ✓ | ✓ | |
| Customers | `customers.customer.read`, `customers.customer.write` | ✓ | ✓ | |
| Money (`expenses`) | `finance.entry.read`, `finance.expense.write`, `finance.purchase.write`, `finance.income.write` | ✓ | ✓ | |
| Reports | `reports.report.read`, `reports.export.run` | ✓ | ✓ | |
| Team | `team.member.read`, `team.member.manage`, `team.role.read`, `team.role.manage`, `audit.event.read` | ✓ | | |
| Settings | `settings.shop.write` | ✓ | | |

The system `customer` role (bound to every CUSTOMER account at ORGANIZATION scope) grants `shop.order.place`, `shop.review.write`, `shop.wishlist.manage`. Customer access to their own data is also checked by ownership in every use case.

## audit_event (append-only; UPDATE/DELETE revoked from the application role)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `occurred_at` | timestamptz default now() | Y | | |
| `organization_id` | uuid | Y | | scope |
| `actor_id` / `actor_kind` | uuid / text (`STAFF`,`CUSTOMER`,`SYSTEM`,`ANONYMOUS`) | - / Y | | who |
| `action` | text, e.g. `team.member.invite`, `team.role.update`, `auth.sign_in.failed` | Y | | what |
| `resource_type` / `resource_id` | text / text | Y / - | | target |
| `scope_type` / `scope_id` | text / uuid | - | | where |
| `outcome` | text check (`SUCCESS`,`DENIED`,`FAILED`) | Y | | |
| `correlation_id` | text | Y | | ties to logs |
| `ip_hash` / `user_agent` | bytea / text ≤ 200 | - | PII (pseudonymous) | abuse analysis |
| `details` | jsonb (redacted: never passwords, tokens or full PII) | - | | e.g. role diff |

Read access: `audit.event.read` only. Retention: 2 years, then partitions are dropped (monthly partitions by `occurred_at`).

## Seed data (owner rule: ≥ 4 rows per table; profiles dev/test/testing/staging only)
Scripts: `backend/src/main/resources/db/seed/R__seed_01_identity.sql` (after `R__seed_00_organization.sql`). Fixed UUIDs use the prefix `01970000-0000-7000-8001-` + 12 digits (table number + row). All e-mails end in `@example.test`; phones use the Ofcom drama range `+447700900xxx`. **Passwords are never committed:** the backend hashes `AURELLE_SEED_PASSWORD` with Argon2id at startup and passes it to Flyway as the placeholder `${seed_password_hash}`; without the variable, seeded accounts get no usable password (sign-in fails safely).

| Table | Seed rows (name → what it covers) |
| --- | --- |
| `organization` | `aurelle` (default) + 3 rows from phase 0 seed (all fields, required only, archived) |
| `location` | Online store (`ONLINE`, default, required only) · Kigali studio (`STORE`, every field incl. GPS + landmark) · Accra warehouse (`WAREHOUSE`) · Remera pop-up (`STORE`, `active=false`) |
| `user_account` | Staff: Zara Bello owner@ (ACTIVE, all fields) · Daniel Osei manager@ · Linda Cole packer@ · Grace Mensah invited@ (`INVITED`, no password). Customers: Amina Okoye amina@ (e-mail + phone verified, marketing consent, DARK theme) · Kwame Mensah (phone only, required fields only) · Nia Kamau nia@ (`LOCKED`, `failed_sign_in_count=5`, `locked_until` in the future) · Kofi Boateng kofi@ (`INACTIVE`) |
| `customer_profile` | Amina (every field, `shop_for={WOMEN,KIDS_UNISEX}`, birthday, source INSTAGRAM, staff note) · Kwame (required only) · Nia (`shop_for={MEN}`) · Kofi (`is_guest=false`, empty arrays) |
| `staff_profile` | Zara (job title Owner, default location Online store) · Daniel (Manager, Kigali studio) · Linda (Packer, Accra warehouse) · Grace (required only) |
| `auth_session` | Amina WEB (active) · Amina ANDROID (active) · Daniel WEB (`revoked_reason=SIGN_OUT`) · Linda ANDROID (expired). Token hashes are random bytes; no seeded session can be used to sign in |
| `auth_token` | Grace `STAFF_INVITE` (unused, valid 72 h) · Amina `EMAIL_VERIFY` (used) · Kwame `PASSWORD_RESET` (expired) · Nia `PASSWORD_RESET` (unused, superseded) |
| `permission` | Full catalogue below (reference data: 28 staff keys + 3 customer keys) |
| `role` | System: `owner`, `manager`, `packer`, `customer`. Custom: `weekend-lead` (inherits packer + notifications) · `stock-auditor` (archived) |
| `role_permission` | Grants per the catalogue table below, plus `weekend-lead` DENY `orders.order.refund` (explicit DENY example). DENY is never placed on a role that others inherit from unless intended: it propagates to every descendant (owner → manager) |
| `role_inheritance` | owner → manager · manager → packer · weekend-lead → packer · stock-auditor → packer |
| `role_binding` | Zara owner @ORGANIZATION · Daniel manager @ORGANIZATION · Linda packer @LOCATION Accra warehouse · Grace weekend-lead @LOCATION Kigali (expires in 30 days) · one revoked binding (Daniel packer, revoked) · each customer → `customer` @ORGANIZATION |
| `principal_permission_override` | Linda ALLOW `catalog.product.read` · Daniel DENY `finance.expense.write` · Grace ALLOW `notifications.inbox.read` · Linda DENY `orders.order.refund` (no-op DENY, still evaluated) |
| `audit_event` | `auth.sign_in` SUCCESS (Amina) · `auth.sign_in.failed` FAILED (Nia) · `team.member.invite` SUCCESS (Zara → Grace) · `team.role.update` DENIED (Daniel tried to edit `owner`) |
