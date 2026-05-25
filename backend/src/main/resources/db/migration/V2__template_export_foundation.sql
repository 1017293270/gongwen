create table document_template (
    id bigserial primary key,
    template_name varchar(200) not null,
    version_no integer not null default 1,
    document_type_code varchar(50),
    file_path varchar(500),
    status varchar(30) not null default 'DRAFT',
    tenant_id bigint,
    department_id bigint,
    created_by bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (template_name, version_no)
);

create table template_field (
    id bigserial primary key,
    template_id bigint not null references document_template(id) on delete cascade,
    field_key varchar(100) not null,
    field_label varchar(100) not null,
    field_type varchar(50) not null default 'TEXT',
    required boolean not null default true,
    sort_order integer not null default 0,
    default_value varchar(500),
    created_at timestamptz not null default now(),
    unique (template_id, field_key)
);

create table export_record (
    id bigserial primary key,
    template_id bigint,
    template_name varchar(200) not null,
    template_version integer not null,
    draft_id bigint,
    exported_by bigint,
    file_name varchar(255) not null,
    file_path varchar(500),
    status varchar(30) not null,
    error_code varchar(100),
    error_message varchar(500),
    created_at timestamptz not null default now()
);

create index idx_template_field_template_id on template_field(template_id);
create index idx_export_record_draft_id on export_record(draft_id);
create index idx_export_record_created_at on export_record(created_at);
