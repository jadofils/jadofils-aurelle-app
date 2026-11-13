# Phase 1 — Identity, access, organization (status: draft)

Modules: `identity`, `authorization`, `organization`, `audit`.

## organization
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `name` | text | Y | | legal/brand name ("Aurelle") |
| `slug` | citext unique | Y | | stable key |
| `default_currency` | char(3) | Y | | ISO 4217 |
| `default_locale` | text | Y | | BCP 47, e.g. `en-RW` |
| `time_zone` | text | Y | | IANA zone for reports and "today" |
| `support_email` / `support_phone` | text | - | | shown in footer, emails |
| `tax_id` | text | - | | invoices (D11) |
| `logo_media_id` | uuid | - | | invoices, emails |

## location
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `organization_id` | uuid | Y | | scope |
| `name` | text | Y | | "Kigali studio", "Online warehouse" |
| `kind` | text check (`STORE`,`WAREHOUSE`,`ONLINE`) | Y | | stock and staff scope |
| `address_*` | same columns as `address` | - | | pick-up, returns |
| `time_zone` | text | - | | overrides organization |
| `active` | boolean | Y | | |

## user_account
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `kind` | text check (`CUSTOMER`,`STAFF`) | Y | | separates self-service from staff policies |
| `email` | citext unique (partial: where not null) | - | PII | sign-in, receipts; required unless phone is set |
| `email_verified_at` | timestamptz | - | | |
| `phone_e164` | text unique (partial) | - | PII | sign-in/SMS where email is rare |
| `phone_verified_at` | timestamptz | - | | |
| `password_hash` | text | - | secret | null for guests/passwordless; Argon2id or bcrypt |
| `status` | text check (`ACTIVE`,`INACTIVE`,`LOCKED`,`PENDING_VERIFICATION`) | Y | | prototype `active` flag |
| `failed_sign_in_count` / `locked_until` | int / timestamptz | Y / - | | brute-force protection |
| `last_sign_in_at` | timestamptz | - | | |
| `preferred_locale` / `preferred_currency` | text / char(3) | - | | |
| `theme_preference` | text check (`SYSTEM`,`DARK`,`LIGHT`) | - | | sun/moon toggle synced across devices |
| `marketing_consent_at` / `marketing_consent_withdrawn_at` | timestamptz | - | | consent record |
| `terms_accepted_at` / `terms_version` | timestamptz / text | - | | |
| `erased_at` | timestamptz | - | | anonymisation marker |
| CHECK | `email IS NOT NULL OR phone_e164 IS NOT NULL` | | | |

## customer_profile (1:1 with CUSTOMER accounts; also created for guest orders without an account)
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `user_account_id` | uuid unique | - | | null for pure guest buyers |
| `display_name` | text | Y | PII | "Amina Okoye" |
| `given_name` / `family_name` | text | - | PII | greetings, invoices |
| `avatar_media_id` | uuid | - | PII | top-right profile photo |
| `birth_date` | date | - | PII | birthday perk (opt-in) |
| `gender_identity` | text | - | PII | free text, never required |
| `shop_for` | text[] (`MEN`,`WOMEN`,`BOYS`,`GIRLS`,`KIDS`) | - | | default audience tab, personalisation |
| `favourite_category_ids` | uuid[] | - | | personalisation |
| `source` | text | - | | how they found us (`INSTAGRAM`, `FRIEND`, …) |
| `referral_code` | text unique | - | | invite-a-friend later |
| `is_guest` | boolean | Y | | prototype `accountGuest` |
| `status_note` | text | - | PII | staff-only CRM note |

## customer_fit_profile (opt-in; only stored when the customer chooses "save my sizes")
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `customer_profile_id` | uuid | Y | | can have several (self, child) |
| `label` | text | Y | | "Me", "Kofi (son)" |
| `audience` | text | Y | | |
| `top_size` / `bottom_waist` / `shoe_size_eu` | text | - | | |
| `height_cm` / `weight_kg` / `age_years` | smallint / numeric(4,1) / smallint | - | PII (sensitive) | size finder |
| `fit_preference` | text check (`SLIM`,`REGULAR`,`RELAXED`) | - | | |
| `consent_at` | timestamptz | Y | | |

## staff_profile
| Field | Type | Req | PII | Purpose |
| --- | --- | --- | --- | --- |
| `user_account_id` | uuid unique | Y | | |
| `display_name` | text | Y | PII | |
| `job_title` | text | - | | "Packer" (label only; access comes from bindings) |
| `avatar_media_id` | uuid | - | PII | |
| `default_location_id` | uuid | - | | |
| `phone_e164` | text | - | PII | |

## Credentials and sessions
- `auth_token` — `user_account_id`, `purpose` (`EMAIL_VERIFY`,`PASSWORD_RESET`,`INVITE`), `token_hash` (never the token), `expires_at`, `used_at`, `requested_ip_hash`. Single-use, short expiry.
- `session` / `refresh_token` (per D5) — `user_account_id`, `token_hash`, `device_label`, `platform` (`WEB`,`ANDROID`), `created_at`, `last_seen_at`, `expires_at`, `revoked_at`. Lets customers see and sign out other devices.

## Authorization (data, not enums)
- `permission` — `key` unique (e.g. `catalog.product.write`, `catalog.product.read_cost`, `inventory.stock.adjust`, `orders.order.update_status`, `finance.expense.write`, `team.member.manage`), `description`, `introduced_in_version`. Seeded by migration; keys are stable.
- `role` — `organization_id`, `key`, `name`, `description`, `is_system` (seeded Owner/Manager/Packer/Customer cannot be deleted, but grants are editable).
- `role_permission` — `role_id`, `permission_id`, `effect` (`ALLOW`,`DENY`; DENY wins).
- `role_inheritance` — `parent_role_id`, `child_role_id`; DAG, cycle check on insert.
- `role_binding` — `principal_id` (user), `role_id`, `scope_type` (`ORGANIZATION`,`LOCATION`), `scope_id`, `granted_by`, `granted_at`, `expires_at` (-), `revoked_at` (-).
- Prototype page permissions (`dash, notifications, products, stock, orders, promos, customers, expenses, reports, team, settings`) map to permission groups; the client receives `viewer.permissions` to decide navigation.

## audit_event (append-only)
`occurred_at`, `actor_id` (-), `actor_kind`, `action`, `resource_type`, `resource_id`, `scope_type`/`scope_id`, `outcome` (`SUCCESS`,`DENIED`,`FAILED`), `correlation_id`, `ip_hash` (-), `user_agent` (-), `details` jsonb (redacted). Restricted read permission `audit.event.read`; retention defined in D-decision.
