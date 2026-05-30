create table document_structure_profile (
    id bigserial primary key,
    template_version_id bigint not null references document_template_version(id) on delete cascade,
    source_file_hash varchar(128) not null,
    schema_version integer not null,
    extractor_version varchar(40) not null,
    node_count integer not null,
    risk_count integer not null,
    profile_json jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_document_structure_profile_template_version unique (template_version_id)
);

create index idx_document_structure_profile_template_version_id on document_structure_profile(template_version_id);
