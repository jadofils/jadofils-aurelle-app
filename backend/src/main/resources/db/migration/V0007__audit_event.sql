-- Module: audit. Data model: contracts/data-model/01-identity-access.md (audit_event).
-- Append-only: a trigger rejects UPDATE and DELETE for every role, including the application's.

create table audit_event (
    id              uuid        primary key default uuidv7(),
    occurred_at     timestamptz not null default now(),
    organization_id uuid        not null references organization (id),
    actor_id        uuid,
    actor_kind      text        not null check (actor_kind in ('STAFF', 'CUSTOMER', 'SYSTEM', 'ANONYMOUS')),
    action          text        not null check (action ~ '^[a-z_]+(\.[a-z_]+)+$'),
    resource_type   text        not null check (length(resource_type) between 1 and 60),
    resource_id     text        check (resource_id is null or length(resource_id) <= 100),
    scope_type      text        check (scope_type is null or scope_type in ('ORGANIZATION', 'LOCATION')),
    scope_id        uuid,
    outcome         text        not null check (outcome in ('SUCCESS', 'DENIED', 'FAILED')),
    correlation_id  text        not null check (length(correlation_id) between 1 and 100),
    ip_hash         bytea,
    user_agent      text        check (user_agent is null or length(user_agent) <= 200),
    details         jsonb       check (details is null or jsonb_typeof(details) = 'object')
);

create index audit_event_by_org_time on audit_event (organization_id, occurred_at desc);
create index audit_event_by_actor on audit_event (actor_id, occurred_at desc) where actor_id is not null;

create function audit_event_is_append_only() returns trigger language plpgsql as $$
begin
    raise exception 'audit_event is append-only' using errcode = 'restrict_violation';
end;
$$;

create trigger audit_event_no_update_or_delete
    before update or delete on audit_event
    for each row execute function audit_event_is_append_only();

comment on table audit_event is 'Append-only. Retention 2 years (partitioning by month when volume requires it).';
