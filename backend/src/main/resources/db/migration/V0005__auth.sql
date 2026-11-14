-- Module: identity. Data model: contracts/data-model/01-identity-access.md (auth_session, auth_token).
-- Decision D5: opaque tokens; only SHA-256 hashes are stored.

create table auth_session (
    id                          uuid        primary key default uuidv7(),
    user_account_id             uuid        not null references user_account (id),
    platform                    text        not null check (platform in ('WEB', 'ANDROID')),
    device_label                text        check (device_label is null or length(device_label) <= 80),
    access_token_hash           bytea       not null unique check (length(access_token_hash) = 32),
    access_expires_at           timestamptz not null,
    refresh_token_hash          bytea       not null unique check (length(refresh_token_hash) = 32),
    refresh_expires_at          timestamptz not null,
    previous_refresh_token_hash bytea       check (previous_refresh_token_hash is null or length(previous_refresh_token_hash) = 32),
    created_at                  timestamptz not null default now(),
    last_seen_at                timestamptz not null default now(),
    revoked_at                  timestamptz,
    revoked_reason              text        check (revoked_reason is null or revoked_reason in
                                            ('SIGN_OUT', 'REVOKED_BY_USER', 'PASSWORD_CHANGED', 'DEACTIVATED', 'REFRESH_REUSE', 'ADMIN')),
    check ((revoked_at is null) = (revoked_reason is null))
);

create index auth_session_active_by_account on auth_session (user_account_id) where revoked_at is null;
create index auth_session_previous_refresh on auth_session (previous_refresh_token_hash) where previous_refresh_token_hash is not null;

create table auth_token (
    id                uuid        primary key default uuidv7(),
    user_account_id   uuid        not null references user_account (id),
    purpose           text        not null check (purpose in ('EMAIL_VERIFY', 'PASSWORD_RESET', 'STAFF_INVITE')),
    token_hash        bytea       not null unique check (length(token_hash) = 32),
    code_hash         bytea       check (code_hash is null or length(code_hash) = 32),
    failed_attempts   smallint    not null default 0 check (failed_attempts between 0 and 5),
    expires_at        timestamptz not null,
    used_at           timestamptz,
    requested_ip_hash bytea,
    created_at        timestamptz not null default now(),
    check (purpose <> 'STAFF_INVITE' or code_hash is null)
);

create index auth_token_open_by_account on auth_token (user_account_id, purpose) where used_at is null;
