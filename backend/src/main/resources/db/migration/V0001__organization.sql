-- Module: organization. Data model: contracts/data-model/01-identity-access.md (organization).
-- Requires PostgreSQL 18+ for the built-in uuidv7() (decision D23).

create extension if not exists citext;

create table organization (
    id               uuid        primary key default uuidv7(),
    name             text        not null check (length(btrim(name)) between 1 and 200),
    slug             citext      not null unique check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    default_currency char(3)     not null check (default_currency ~ '^[A-Z]{3}$'),
    default_locale   text        not null check (default_locale ~ '^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$'),
    time_zone        text        not null check (length(time_zone) between 1 and 64),
    support_email    text        check (support_email is null or support_email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    support_phone    text        check (support_phone is null or support_phone ~ '^\+[1-9][0-9]{6,14}$'),
    tax_id           text        check (tax_id is null or length(tax_id) <= 64),
    logo_media_id    uuid,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint      not null default 0,
    archived_at      timestamptz
);

comment on table organization is 'Legal/brand entity owning shops and locations. One default row until multi-tenancy (D15) is decided.';
comment on column organization.logo_media_id is 'FK to media(id) added with the media module (phase 2).';

-- The one default organization (data-model rule: seed one default organization).
-- Currency, locale and time zone are editable settings; final values depend on D9/D11.
insert into organization (name, slug, default_currency, default_locale, time_zone)
values ('Aurelle', 'aurelle', 'USD', 'en', 'UTC');
