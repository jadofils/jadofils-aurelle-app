-- Test seed: Phase 1 identity, sessions, authorization, audit (contracts/data-model/01-identity-access.md, "Seed data").
-- Loaded only by the `seed` profile group (dev, test, testing, staging). NEVER in production.
-- Idempotent. Fixed UUIDs: 01970000-0000-7000-8001-0000000TTRRR (TT = table, RRR = row).
-- E-mails @example.test, phones from the Ofcom drama range. Passwords: ${seed_password_hash} is an Argon2id hash
-- of AURELLE_SEED_PASSWORD computed at startup (random and unusable when the variable is not set).

-- location (Online store comes from V0003)
insert into location (id, organization_id, name, kind, country_code, admin_area_1, admin_area_2, admin_area_3,
                      admin_area_4, locality, street_line_1, street_line_2, postal_code, landmark, latitude, longitude,
                      location_accuracy_m, phone_e164, time_zone)
select '01970000-0000-7000-8001-000000001001', id, 'Kigali studio', 'STORE', 'RW', 'Kigali City', 'Gasabo', 'Kimihurura',
       'Rugando', 'Kigali', 'KG 7 Ave 12', 'Ground floor', '00000', 'Opposite the green gate', -1.949000, 30.085000, 15,
       '+447700900101', 'Africa/Kigali'
  from organization where slug = 'aurelle'
on conflict (id) do nothing;

insert into location (id, organization_id, name, kind)
select '01970000-0000-7000-8001-000000001002', id, 'Accra warehouse', 'WAREHOUSE' from organization where slug = 'aurelle'
on conflict (id) do nothing;

insert into location (id, organization_id, name, kind, active)
select '01970000-0000-7000-8001-000000001003', id, 'Remera pop-up', 'STORE', false from organization where slug = 'aurelle'
on conflict (id) do nothing;

-- user_account: 4 staff (owner, manager, packer, invited) + 4 customers (all fields, phone only, locked, inactive)
insert into user_account (id, organization_id, kind, email, email_verified_at, phone_e164, phone_verified_at,
                          password_hash, password_changed_at, status, failed_sign_in_count, locked_until,
                          last_sign_in_at, preferred_locale, preferred_currency, theme_preference,
                          marketing_consent_at, terms_accepted_at, terms_version)
select v.id::uuid, o.id, v.kind, v.email, v.email_verified_at::timestamptz, v.phone, v.phone_verified_at::timestamptz,
       case when v.status = 'INVITED' then null else '${seed_password_hash}' end,
       case when v.status = 'INVITED' then null else timestamptz '2026-11-01 08:00:00+00' end,
       v.status, v.failed, v.locked_until::timestamptz, v.last_sign_in::timestamptz, v.locale, v.currency, v.theme,
       v.marketing::timestamptz, v.terms::timestamptz, v.terms_version
  from organization o
 cross join (values
    ('01970000-0000-7000-8001-000000002001', 'STAFF', 'owner@example.test', '2026-10-01 08:00:00+00', '+447700900011', '2026-10-01 08:05:00+00', 'ACTIVE', 0, null, '2026-11-12 18:30:00+00', 'en-RW', 'USD', 'DARK', null, '2026-10-01 08:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002002', 'STAFF', 'manager@example.test', '2026-10-02 08:00:00+00', null, null, 'ACTIVE', 0, null, '2026-11-12 09:00:00+00', null, null, 'SYSTEM', null, '2026-10-02 08:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002003', 'STAFF', 'packer@example.test', '2026-10-03 08:00:00+00', null, null, 'ACTIVE', 0, null, '2026-11-11 07:45:00+00', null, null, 'LIGHT', null, '2026-10-03 08:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002004', 'STAFF', 'invited@example.test', null, null, null, 'INVITED', 0, null, null, null, null, 'SYSTEM', null, null, null),
    ('01970000-0000-7000-8001-000000002005', 'CUSTOMER', 'amina@example.test', '2026-10-05 10:00:00+00', '+447700900021', '2026-10-05 10:02:00+00', 'ACTIVE', 0, null, '2026-11-13 07:10:00+00', 'en-RW', 'USD', 'DARK', '2026-10-05 10:00:00+00', '2026-10-05 10:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002006', 'CUSTOMER', null, null, '+447700900022', null, 'ACTIVE', 0, null, null, null, null, 'SYSTEM', null, '2026-10-20 12:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002007', 'CUSTOMER', 'nia@example.test', null, null, null, 'LOCKED', 5, '2099-01-01 00:00:00+00', '2026-11-01 12:00:00+00', null, null, 'SYSTEM', null, '2026-10-21 12:00:00+00', '2026-10'),
    ('01970000-0000-7000-8001-000000002008', 'CUSTOMER', 'kofi@example.test', '2026-10-22 12:00:00+00', null, null, 'INACTIVE', 0, null, '2026-10-30 12:00:00+00', null, null, 'SYSTEM', null, '2026-10-22 12:00:00+00', '2026-10')
 ) as v(id, kind, email, email_verified_at, phone, phone_verified_at, status, failed, locked_until, last_sign_in, locale,
        currency, theme, marketing, terms, terms_version)
 where o.slug = 'aurelle'
on conflict (id) do nothing;

-- customer_profile
insert into customer_profile (id, organization_id, user_account_id, display_name, given_name, family_name, birth_date,
                              shop_for, acquisition_source, referral_code, staff_note)
select '01970000-0000-7000-8001-000000003001', o.id, '01970000-0000-7000-8001-000000002005', 'Amina Okoye', 'Amina',
       'Okoye', date '1994-03-14', array['WOMEN', 'KIDS_UNISEX'], 'INSTAGRAM', 'AMINA420',
       'Prefers delivery after 5 pm. 420 loyalty points in the prototype.'
  from organization o where o.slug = 'aurelle'
on conflict (id) do nothing;

insert into customer_profile (id, organization_id, user_account_id, display_name, shop_for)
select v.id::uuid, o.id, v.account::uuid, v.name, v.shop_for::text[]
  from organization o
 cross join (values
    ('01970000-0000-7000-8001-000000003002', '01970000-0000-7000-8001-000000002006', 'Kwame Mensah', '{}'),
    ('01970000-0000-7000-8001-000000003003', '01970000-0000-7000-8001-000000002007', 'Nia Kamau', '{MEN}'),
    ('01970000-0000-7000-8001-000000003004', '01970000-0000-7000-8001-000000002008', 'Kofi Boateng', '{}')
 ) as v(id, account, name, shop_for)
 where o.slug = 'aurelle'
on conflict (id) do nothing;

-- staff_profile
insert into staff_profile (id, user_account_id, display_name, job_title, default_location_id)
select v.id::uuid, v.account::uuid, v.name, v.title, v.location::uuid
  from (values
    ('01970000-0000-7000-8001-000000004001', '01970000-0000-7000-8001-000000002001', 'Zara Bello', 'Owner', null),
    ('01970000-0000-7000-8001-000000004002', '01970000-0000-7000-8001-000000002002', 'Daniel Osei', 'Manager', '01970000-0000-7000-8001-000000001001'),
    ('01970000-0000-7000-8001-000000004003', '01970000-0000-7000-8001-000000002003', 'Linda Cole', 'Packer', '01970000-0000-7000-8001-000000001002'),
    ('01970000-0000-7000-8001-000000004004', '01970000-0000-7000-8001-000000002004', 'Grace Mensah', null, null)
 ) as v(id, account, name, title, location)
on conflict (id) do nothing;

update staff_profile
   set default_location_id = (select l.id from location l join organization o on o.id = l.organization_id
                               where o.slug = 'aurelle' and l.name = 'Online store')
 where id = '01970000-0000-7000-8001-000000004001' and default_location_id is null;

-- auth_session: hashes of throwaway strings, so no seeded session can be used to sign in
insert into auth_session (id, user_account_id, platform, device_label, access_token_hash, access_expires_at,
                          refresh_token_hash, refresh_expires_at, created_at, last_seen_at, revoked_at, revoked_reason)
select v.id::uuid, v.account::uuid, v.platform, v.label, sha256(convert_to('seed-access-' || v.id, 'UTF8')),
       v.access_exp::timestamptz, sha256(convert_to('seed-refresh-' || v.id, 'UTF8')), v.refresh_exp::timestamptz,
       timestamptz '2026-11-01 09:00:00+00', v.seen::timestamptz, v.revoked::timestamptz, v.reason
  from (values
    ('01970000-0000-7000-8001-000000005001', '01970000-0000-7000-8001-000000002005', 'WEB', 'Chrome on Windows', '2099-01-01 00:00:00+00', '2099-01-01 00:00:00+00', '2026-11-13 07:10:00+00', null, null),
    ('01970000-0000-7000-8001-000000005002', '01970000-0000-7000-8001-000000002005', 'ANDROID', 'Pixel 8', '2099-01-01 00:00:00+00', '2099-01-01 00:00:00+00', '2026-11-12 20:00:00+00', null, null),
    ('01970000-0000-7000-8001-000000005003', '01970000-0000-7000-8001-000000002002', 'WEB', 'Firefox on Ubuntu', '2026-11-02 09:15:00+00', '2026-12-01 09:00:00+00', '2026-11-02 09:00:00+00', '2026-11-02 18:00:00+00', 'SIGN_OUT'),
    ('01970000-0000-7000-8001-000000005004', '01970000-0000-7000-8001-000000002003', 'ANDROID', null, '2026-11-01 09:15:00+00', '2026-11-05 09:00:00+00', '2026-11-04 09:00:00+00', null, null)
 ) as v(id, account, platform, label, access_exp, refresh_exp, seen, revoked, reason)
on conflict (id) do nothing;

-- auth_token
insert into auth_token (id, user_account_id, purpose, token_hash, code_hash, failed_attempts, expires_at, used_at)
select v.id::uuid, v.account::uuid, v.purpose, sha256(convert_to('seed-token-' || v.id, 'UTF8')),
       case when v.purpose = 'STAFF_INVITE' then null else sha256(convert_to('seed-code-' || v.id, 'UTF8')) end,
       v.failed, v.expires::timestamptz, v.used::timestamptz
  from (values
    ('01970000-0000-7000-8001-000000006001', '01970000-0000-7000-8001-000000002004', 'STAFF_INVITE', 0, '2099-01-01 00:00:00+00', null),
    ('01970000-0000-7000-8001-000000006002', '01970000-0000-7000-8001-000000002005', 'EMAIL_VERIFY', 0, '2026-10-06 10:00:00+00', '2026-10-05 10:02:00+00'),
    ('01970000-0000-7000-8001-000000006003', '01970000-0000-7000-8001-000000002006', 'PASSWORD_RESET', 2, '2026-11-01 12:30:00+00', null),
    ('01970000-0000-7000-8001-000000006004', '01970000-0000-7000-8001-000000002007', 'PASSWORD_RESET', 5, '2099-01-01 00:00:00+00', '2026-11-01 12:10:00+00')
 ) as v(id, account, purpose, failed, expires, used)
on conflict (id) do nothing;

-- role: two custom roles next to the four system roles from V0006
insert into role (id, organization_id, key, name, description, kind, archived_at)
select v.id::uuid, o.id, v.key, v.name, v.description, 'CUSTOM', v.archived::timestamptz
  from organization o
 cross join (values
    ('01970000-0000-7000-8001-000000007001', 'weekend-lead', 'Weekend lead', 'Packer plus the notification inbox; cannot refund', null),
    ('01970000-0000-7000-8001-000000007002', 'stock-auditor', 'Stock auditor', 'Read-only stock checks (retired)', '2026-10-31 18:00:00+00')
 ) as v(id, key, name, description, archived)
 where o.slug = 'aurelle'
on conflict (id) do nothing;

insert into role_permission (role_id, permission_key, effect) values
    ('01970000-0000-7000-8001-000000007001', 'notifications.inbox.read', 'ALLOW'),
    ('01970000-0000-7000-8001-000000007001', 'orders.order.refund', 'DENY'),
    ('01970000-0000-7000-8001-000000007002', 'inventory.stock.read', 'ALLOW')
on conflict do nothing;

-- role_inheritance (child inherits the parent's grants)
insert into role_inheritance (child_role_id, parent_role_id)
select c.id, p.id
  from (values ('owner', 'manager'), ('manager', 'packer'), ('weekend-lead', 'packer'), ('stock-auditor', 'packer'))
       as v(child, parent)
  join organization o on o.slug = 'aurelle'
  join role c on c.organization_id = o.id and c.key = v.child
  join role p on p.organization_id = o.id and p.key = v.parent
on conflict do nothing;

-- role_binding
insert into role_binding (id, user_account_id, role_id, scope_type, scope_id, granted_by, granted_at, expires_at,
                          revoked_at, revoked_by)
select v.id::uuid, v.account::uuid, r.id, v.scope_type,
       case when v.scope_type = 'ORGANIZATION' then o.id else v.scope_id::uuid end,
       v.granted_by::uuid, timestamptz '2026-10-01 08:00:00+00', v.expires::timestamptz, v.revoked::timestamptz,
       case when v.revoked is null then null else '01970000-0000-7000-8001-000000002001'::uuid end
  from (values
    ('01970000-0000-7000-8001-000000008001', '01970000-0000-7000-8001-000000002001', 'owner', 'ORGANIZATION', null, null, null, null),
    ('01970000-0000-7000-8001-000000008002', '01970000-0000-7000-8001-000000002002', 'manager', 'ORGANIZATION', null, '01970000-0000-7000-8001-000000002001', null, null),
    ('01970000-0000-7000-8001-000000008003', '01970000-0000-7000-8001-000000002003', 'packer', 'LOCATION', '01970000-0000-7000-8001-000000001002', '01970000-0000-7000-8001-000000002001', null, null),
    ('01970000-0000-7000-8001-000000008004', '01970000-0000-7000-8001-000000002004', 'weekend-lead', 'LOCATION', '01970000-0000-7000-8001-000000001001', '01970000-0000-7000-8001-000000002001', '2099-01-01 00:00:00+00', null),
    ('01970000-0000-7000-8001-000000008005', '01970000-0000-7000-8001-000000002002', 'packer', 'ORGANIZATION', null, '01970000-0000-7000-8001-000000002001', null, '2026-10-15 08:00:00+00'),
    ('01970000-0000-7000-8001-000000008006', '01970000-0000-7000-8001-000000002005', 'customer', 'ORGANIZATION', null, null, null, null),
    ('01970000-0000-7000-8001-000000008007', '01970000-0000-7000-8001-000000002006', 'customer', 'ORGANIZATION', null, null, null, null),
    ('01970000-0000-7000-8001-000000008008', '01970000-0000-7000-8001-000000002007', 'customer', 'ORGANIZATION', null, null, null, null),
    ('01970000-0000-7000-8001-000000008009', '01970000-0000-7000-8001-000000002008', 'customer', 'ORGANIZATION', null, null, null, null)
 ) as v(id, account, role_key, scope_type, scope_id, granted_by, expires, revoked)
  join organization o on o.slug = 'aurelle'
  join role r on r.organization_id = o.id and r.key = v.role_key
on conflict (id) do nothing;

-- principal_permission_override
insert into principal_permission_override (id, user_account_id, permission_key, effect, scope_type, scope_id, granted_by)
select v.id::uuid, v.account::uuid, v.permission, v.effect, 'ORGANIZATION', o.id, '01970000-0000-7000-8001-000000002001'
  from (values
    ('01970000-0000-7000-8001-000000009001', '01970000-0000-7000-8001-000000002003', 'catalog.product.read', 'ALLOW'),
    ('01970000-0000-7000-8001-000000009002', '01970000-0000-7000-8001-000000002002', 'finance.expense.write', 'DENY'),
    ('01970000-0000-7000-8001-000000009003', '01970000-0000-7000-8001-000000002004', 'notifications.inbox.read', 'ALLOW'),
    ('01970000-0000-7000-8001-000000009004', '01970000-0000-7000-8001-000000002003', 'orders.order.refund', 'DENY')
 ) as v(id, account, permission, effect)
  join organization o on o.slug = 'aurelle'
on conflict (id) do nothing;

-- audit_event (append-only; inserts only)
insert into audit_event (id, occurred_at, organization_id, actor_id, actor_kind, action, resource_type, resource_id,
                         scope_type, scope_id, outcome, correlation_id, details)
select v.id::uuid, v.at::timestamptz, o.id, v.actor::uuid, v.actor_kind, v.action, v.resource_type, v.resource_id,
       'ORGANIZATION', o.id, v.outcome, v.correlation, v.details::jsonb
  from (values
    ('01970000-0000-7000-8001-000000010001', '2026-11-13 07:10:00+00', '01970000-0000-7000-8001-000000002005', 'CUSTOMER', 'auth.sign_in', 'user_account', '01970000-0000-7000-8001-000000002005', 'SUCCESS', 'seed-correlation-1', '{"platform": "WEB"}'),
    ('01970000-0000-7000-8001-000000010002', '2026-11-01 12:00:00+00', null, 'ANONYMOUS', 'auth.sign_in', 'user_account', null, 'FAILED', 'seed-correlation-2', '{"reason": "INVALID_CREDENTIALS"}'),
    ('01970000-0000-7000-8001-000000010003', '2026-11-10 09:00:00+00', '01970000-0000-7000-8001-000000002001', 'STAFF', 'team.member.invite', 'user_account', '01970000-0000-7000-8001-000000002004', 'SUCCESS', 'seed-correlation-3', '{"roles": ["weekend-lead"]}'),
    ('01970000-0000-7000-8001-000000010004', '2026-11-11 10:00:00+00', '01970000-0000-7000-8001-000000002002', 'STAFF', 'team.role.update', 'role', 'owner', 'DENIED', 'seed-correlation-4', null)
 ) as v(id, at, actor, actor_kind, action, resource_type, resource_id, outcome, correlation, details)
  join organization o on o.slug = 'aurelle'
on conflict (id) do nothing;
