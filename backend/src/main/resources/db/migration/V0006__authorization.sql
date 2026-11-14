-- Module: authorization. Data model: contracts/data-model/01-identity-access.md (Authorization).
-- Roles, grants and bindings are data, not enums. DENY always wins.

create table permission (
    key              text    primary key check (key ~ '^[a-z]+(\.[a-z_]+)+$'),
    description      text    not null check (length(description) between 1 and 200),
    permission_group text    not null check (length(permission_group) between 1 and 40),
    introduced_in    integer not null check (introduced_in >= 0)
);

create table role (
    id              uuid        primary key default uuidv7(),
    organization_id uuid        not null references organization (id),
    key             text        not null check (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and length(key) <= 40),
    name            text        not null check (length(btrim(name)) between 1 and 80),
    description     text        check (description is null or length(description) <= 300),
    kind            text        not null check (kind in ('SYSTEM', 'CUSTOM')),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint      not null default 0,
    archived_at     timestamptz,
    unique (organization_id, key),
    check (kind = 'CUSTOM' or archived_at is null)
);

create table role_permission (
    role_id        uuid not null references role (id),
    permission_key text not null references permission (key),
    effect         text not null check (effect in ('ALLOW', 'DENY')),
    primary key (role_id, permission_key)
);

create table role_inheritance (
    child_role_id  uuid not null references role (id),
    parent_role_id uuid not null references role (id),
    primary key (child_role_id, parent_role_id),
    check (child_role_id <> parent_role_id)
);

create table role_binding (
    id              uuid        primary key default uuidv7(),
    user_account_id uuid        not null references user_account (id),
    role_id         uuid        not null references role (id),
    scope_type      text        not null check (scope_type in ('ORGANIZATION', 'LOCATION')),
    scope_id        uuid        not null,
    granted_by      uuid        references user_account (id),
    granted_at      timestamptz not null default now(),
    expires_at      timestamptz,
    revoked_at      timestamptz,
    revoked_by      uuid        references user_account (id),
    check (expires_at is null or expires_at > granted_at)
);

create index role_binding_active_by_account on role_binding (user_account_id) where revoked_at is null;
create unique index role_binding_active_uq on role_binding (user_account_id, role_id, scope_type, scope_id)
    where revoked_at is null;

create table principal_permission_override (
    id              uuid        primary key default uuidv7(),
    user_account_id uuid        not null references user_account (id),
    permission_key  text        not null references permission (key),
    effect          text        not null check (effect in ('ALLOW', 'DENY')),
    scope_type      text        not null check (scope_type in ('ORGANIZATION', 'LOCATION')),
    scope_id        uuid        not null,
    granted_by      uuid        references user_account (id),
    granted_at      timestamptz not null default now(),
    unique (user_account_id, permission_key, scope_type, scope_id)
);

-- Permission catalogue (reference data). Keys are stable; groups follow the prototype's pages.
insert into permission (key, description, permission_group, introduced_in) values
    ('reports.dashboard.read',     'See the business dashboard',                       'Dashboard',     1),
    ('notifications.inbox.read',   'Read the shop notification inbox',                 'Notifications', 1),
    ('catalog.product.read',       'See products in the back office',                  'Products',      1),
    ('catalog.product.write',      'Create, edit, hide and archive products',          'Products',      1),
    ('catalog.product.read_cost',  'See product cost and margin',                      'Products',      1),
    ('catalog.category.write',     'Create and edit categories',                       'Products',      1),
    ('media.asset.upload',         'Upload product photos',                            'Products',      1),
    ('inventory.stock.read',       'See stock levels and movements',                   'Stock',         1),
    ('inventory.stock.adjust',     'Adjust stock and record restocks',                 'Stock',         1),
    ('orders.order.read',          'See orders',                                       'Orders',        1),
    ('orders.order.update_status', 'Move orders through Placed, Packed, Shipped, Delivered', 'Orders', 1),
    ('orders.order.refund',        'Refund and return orders',                         'Orders',        1),
    ('promotions.promo.read',      'See promo codes',                                  'Promotions',    1),
    ('promotions.promo.write',     'Create and edit promo codes',                      'Promotions',    1),
    ('customers.customer.read',    'See customer profiles',                            'Customers',     1),
    ('customers.customer.write',   'Edit customer profiles and account status',        'Customers',     1),
    ('finance.entry.read',         'See money in and out',                             'Money',         1),
    ('finance.expense.write',      'Record daily expenses',                            'Money',         1),
    ('finance.purchase.write',     'Record clothing purchases',                        'Money',         1),
    ('finance.income.write',       'Record other income',                              'Money',         1),
    ('reports.report.read',        'See profit and trend reports',                     'Reports',       1),
    ('reports.export.run',         'Export reports as CSV',                            'Reports',       1),
    ('team.member.read',           'See the team',                                     'Team',          1),
    ('team.member.manage',         'Invite staff, change roles, activate and deactivate', 'Team',       1),
    ('team.role.read',             'See roles and permissions',                        'Team',          1),
    ('team.role.manage',           'Create and edit roles',                            'Team',          1),
    ('audit.event.read',           'Read the audit log',                               'Team',          1),
    ('settings.shop.write',        'Change shop settings',                             'Settings',      1),
    ('shop.order.place',           'Place orders',                                     'Shopping',      1),
    ('shop.review.write',          'Write product reviews',                            'Shopping',      1),
    ('shop.wishlist.manage',       'Keep a wishlist',                                  'Shopping',      1);

-- System roles for the default organization, with explicit grants (no inheritance needed).
insert into role (organization_id, key, name, description, kind)
select o.id, r.key, r.name, r.description, 'SYSTEM'
  from organization o
 cross join (values
    ('owner',    'Owner',    'Everything, including money, reports, team and settings'),
    ('manager',  'Manager',  'Runs the shop day to day; no team or settings'),
    ('packer',   'Packer',   'Orders and stock only'),
    ('customer', 'Customer', 'Self-service shopper')) as r(key, name, description)
 where o.slug = 'aurelle';

insert into role_permission (role_id, permission_key, effect)
select r.id, p.key, 'ALLOW'
  from role r
  join permission p on
       (r.key = 'owner'    and p.permission_group not in ('Shopping'))
    or (r.key = 'manager'  and p.permission_group not in ('Shopping', 'Team', 'Settings'))
    or (r.key = 'packer'   and p.key in ('orders.order.read', 'orders.order.update_status',
                                         'inventory.stock.read', 'inventory.stock.adjust'))
    or (r.key = 'customer' and p.permission_group = 'Shopping')
  join organization o on o.id = r.organization_id and o.slug = 'aurelle';
