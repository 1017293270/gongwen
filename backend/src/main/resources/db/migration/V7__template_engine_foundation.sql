create table document_template_version (
    id bigserial primary key,
    template_id bigint not null references document_template(id) on delete cascade,
    version_no integer not null,
    original_file_name varchar(255) not null,
    content_type varchar(150) not null,
    file_size_bytes bigint not null,
    file_path varchar(500) not null,
    profile_hash varchar(128),
    parse_status varchar(30) not null default 'PENDING',
    parse_error_code varchar(100),
    parse_error_message varchar(500),
    created_by bigint,
    created_at timestamptz not null default now(),
    unique (template_id, version_no)
);

create table template_profile (
    id bigserial primary key,
    template_version_id bigint not null unique references document_template_version(id) on delete cascade,
    schema_version integer not null,
    profile_json jsonb not null,
    created_at timestamptz not null default now()
);

create table template_block_mapping (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    block_type varchar(50) not null,
    paragraph_role varchar(80),
    placeholder_key varchar(100),
    style_id varchar(150),
    style_name varchar(200),
    anchor_paragraph_key varchar(100),
    repeat_mode varchar(50) not null default 'SINGLE',
    required boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table template_rule (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    target_type varchar(80) not null,
    target_key varchar(150) not null,
    rule_type varchar(80) not null,
    expected_value jsonb not null,
    severity varchar(30) not null default 'WARNING',
    source varchar(50) not null,
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table template_validation_result (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    severity varchar(30) not null,
    code varchar(100) not null,
    message varchar(500) not null,
    target_type varchar(80),
    target_key varchar(150),
    details_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index idx_template_version_template_id on document_template_version(template_id);
create index idx_template_version_parse_status on document_template_version(parse_status);
create index idx_template_block_mapping_version on template_block_mapping(template_version_id);
create index idx_template_rule_version on template_rule(template_version_id);
create index idx_template_validation_version on template_validation_result(template_version_id);
