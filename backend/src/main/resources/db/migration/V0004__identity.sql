-- Module: identity. Data model: contracts/data-model/01-identity-access.md
-- (user_account, customer_profile, staff_profile). customer_fit_profile arrives with the size finder (phase 2).

create table user_account (
    id                             uuid        primary key default uuidv7(),
    organization_id                uuid        not null references organization (id),
    kind                           text        not null check (kind in ('CUSTOMER', 'STAFF')),
    email                          citext      check (email is null or (length(email) <= 254 and email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')),
    email_verified_at              timestamptz,
    phone_e164                     text        check (phone_e164 is null or phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
    phone_verified_at              timestamptz,
    password_hash                  text        check (password_hash is null or password_hash like '$argon2id$%'),
    password_changed_at            timestamptz,
    status                         text        not null default 'ACTIVE'
                                               check (status in ('ACTIVE', 'INACTIVE', 'LOCKED', 'INVITED')),
    failed_sign_in_count           integer     not null default 0 check (failed_sign_in_count >= 0),
    locked_until                   timestamptz,
    last_sign_in_at                timestamptz,
    preferred_locale               text        check (preferred_locale is null or preferred_locale ~ '^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$'),
    preferred_currency             char(3)     check (preferred_currency is null or preferred_currency ~ '^[A-Z]{3}$'),
    theme_preference               text        not null default 'SYSTEM' check (theme_preference in ('SYSTEM', 'DARK', 'LIGHT')),
    marketing_consent_at           timestamptz,
    marketing_consent_withdrawn_at timestamptz,
    terms_accepted_at              timestamptz,
    terms_version                  text        check (terms_version is null or length(terms_version) <= 40),
    erased_at                      timestamptz,
    created_at                     timestamptz not null default now(),
    updated_at                     timestamptz not null default now(),
    version                        bigint      not null default 0,
    check (email is not null or phone_e164 is not null or erased_at is not null),
    check (status = 'INVITED' or password_hash is not null or erased_at is not null)
);

create unique index user_account_email_uq on user_account (organization_id, email) where email is not null;
create unique index user_account_phone_uq on user_account (organization_id, phone_e164) where phone_e164 is not null;

create table customer_profile (
    id                     uuid        primary key default uuidv7(),
    organization_id        uuid        not null references organization (id),
    user_account_id        uuid        unique references user_account (id),
    display_name           text        not null check (length(btrim(display_name)) between 1 and 120),
    given_name             text        check (given_name is null or length(given_name) <= 80),
    family_name            text        check (family_name is null or length(family_name) <= 80),
    avatar_media_id        uuid,
    birth_date             date        check (birth_date is null or birth_date > date '1900-01-01'),
    shop_for               text[]      not null default '{}'
                                       check (shop_for <@ array['MEN', 'WOMEN', 'BOYS', 'GIRLS', 'KIDS_UNISEX', 'UNISEX']),
    favourite_category_ids uuid[]      not null default '{}',
    acquisition_source     text        check (acquisition_source is null or acquisition_source in
                                       ('INSTAGRAM', 'TIKTOK', 'FACEBOOK', 'FRIEND', 'SEARCH', 'STORE', 'OTHER')),
    referral_code          text        unique check (referral_code is null or referral_code ~ '^[A-Z0-9]{6,12}$'),
    is_guest               boolean     not null default false,
    staff_note             text        check (staff_note is null or length(staff_note) <= 2000),
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    version                bigint      not null default 0,
    archived_at            timestamptz
);

comment on column customer_profile.avatar_media_id is 'FK to media(id) added with the media module (phase 2).';

create table staff_profile (
    id                  uuid        primary key default uuidv7(),
    user_account_id     uuid        not null unique references user_account (id),
    display_name        text        not null check (length(btrim(display_name)) between 1 and 120),
    job_title           text        check (job_title is null or length(job_title) <= 80),
    avatar_media_id     uuid,
    default_location_id uuid        references location (id),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint      not null default 0
);
