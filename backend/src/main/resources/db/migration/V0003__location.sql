-- Module: organization. Data model: contracts/data-model/01-identity-access.md (location).

create table location (
    id                  uuid          primary key default uuidv7(),
    organization_id     uuid          not null references organization (id),
    name                text          not null check (length(btrim(name)) between 1 and 120),
    kind                text          not null check (kind in ('STORE', 'WAREHOUSE', 'ONLINE')),
    country_code        char(2)       check (country_code is null or country_code ~ '^[A-Z]{2}$'),
    admin_area_1        text          check (admin_area_1 is null or length(admin_area_1) <= 120),
    admin_area_2        text          check (admin_area_2 is null or length(admin_area_2) <= 120),
    admin_area_3        text          check (admin_area_3 is null or length(admin_area_3) <= 120),
    admin_area_4        text          check (admin_area_4 is null or length(admin_area_4) <= 120),
    locality            text          check (locality is null or length(locality) <= 120),
    street_line_1       text          check (street_line_1 is null or length(street_line_1) <= 200),
    street_line_2       text          check (street_line_2 is null or length(street_line_2) <= 200),
    postal_code         text          check (postal_code is null or length(postal_code) <= 20),
    landmark            text          check (landmark is null or length(landmark) <= 200),
    latitude            numeric(9, 6) check (latitude is null or latitude between -90 and 90),
    longitude           numeric(9, 6) check (longitude is null or longitude between -180 and 180),
    location_accuracy_m integer       check (location_accuracy_m is null or location_accuracy_m >= 0),
    phone_e164          text          check (phone_e164 is null or phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
    time_zone           text          check (time_zone is null or length(time_zone) between 1 and 64),
    active              boolean       not null default true,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    version             bigint        not null default 0,
    archived_at         timestamptz,
    unique (organization_id, name),
    check ((latitude is null) = (longitude is null))
);

-- Default location (data-model rule: seed one default organization and location).
insert into location (organization_id, name, kind)
select id, 'Online store', 'ONLINE' from organization where slug = 'aurelle';
