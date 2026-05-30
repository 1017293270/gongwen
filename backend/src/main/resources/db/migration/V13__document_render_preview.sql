create table document_render_preview (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    source_file_hash varchar(128),
    renderer varchar(40) not null,
    renderer_version varchar(80),
    status varchar(20) not null,
    page_count integer not null default 0,
    storage_path text,
    manifest_json jsonb not null default '{}'::jsonb,
    error_code varchar(80),
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_document_render_preview_template_version
    on document_render_preview (template_version_id, created_at desc);

create index idx_document_render_preview_status
    on document_render_preview (status);
