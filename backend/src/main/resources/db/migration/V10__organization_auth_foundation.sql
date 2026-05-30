create table department (
    id bigserial primary key,
    parent_id bigint references department(id),
    code varchar(80) not null unique,
    name varchar(120) not null,
    status varchar(30) not null default 'ACTIVE',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table app_role (
    code varchar(80) primary key,
    name varchar(120) not null,
    description varchar(500)
);

create table app_user (
    id bigserial primary key,
    username varchar(80) not null unique,
    display_name varchar(120) not null,
    password_hash varchar(120) not null,
    department_id bigint references department(id),
    status varchar(30) not null default 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table app_user_role (
    user_id bigint not null references app_user(id) on delete cascade,
    role_code varchar(80) not null references app_role(code),
    primary key (user_id, role_code)
);

insert into department (code, name, status, sort_order)
values ('ROOT', U&'\603B\516C\53F8', 'ACTIVE', 1);

insert into app_role (code, name, description)
values
    ('SYSTEM_ADMIN', U&'\7CFB\7EDF\7BA1\7406\5458', 'Manage accounts, departments, document types, and all business data'),
    ('TEMPLATE_ADMIN', U&'\6A21\677F\7BA1\7406\5458', 'Manage templates and document type configuration'),
    ('DRAFTER', U&'\8D77\8349\4EBA', 'Create and manage own drafts, materials, and export records');

alter table document_type
    add column created_by bigint references app_user(id),
    add column department_id bigint references department(id);

alter table export_record
    add column department_id bigint references department(id);

create index idx_department_parent_id on department(parent_id);
create index idx_department_status on department(status);
create index idx_app_user_department_id on app_user(department_id);
create index idx_app_user_status on app_user(status);
create index idx_app_user_role_role_code on app_user_role(role_code);
create index idx_document_type_created_by on document_type(created_by);
create index idx_document_type_department_id on document_type(department_id);
create index idx_document_template_created_by on document_template(created_by);
create index idx_draft_created_by on draft(created_by);
create index idx_export_record_exported_by on export_record(exported_by);
