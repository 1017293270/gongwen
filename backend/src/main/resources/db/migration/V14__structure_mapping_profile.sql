create table structure_mapping_profile (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    version_no integer not null,
    status varchar(30) not null,
    mapping_json jsonb not null,
    validation_json jsonb not null default '[]'::jsonb,
    created_by bigint references app_user(id),
    department_id bigint references department(id),
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (template_version_id, version_no)
);

create index idx_structure_mapping_profile_version
    on structure_mapping_profile (template_version_id, status, version_no desc);
