create table material (
    id bigserial primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    original_file_name varchar(500) not null,
    content_type varchar(200),
    file_size_bytes bigint not null,
    file_extension varchar(20) not null,
    storage_path text not null,
    status varchar(30) not null,
    extracted_text text,
    error_message text,
    tenant_id bigint,
    department_id bigint,
    version_no integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_material_draft_id on material(draft_id);
create index idx_material_status on material(status);
