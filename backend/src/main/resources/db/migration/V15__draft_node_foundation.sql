create table draft_node (
    id bigserial primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    structure_mapping_profile_id bigint references structure_mapping_profile(id),
    template_node_key varchar(160) not null,
    parent_template_node_key varchar(160),
    node_type varchar(50) not null,
    role varchar(50) not null,
    slot_key varchar(120) not null default '',
    title varchar(300) not null default '',
    content text not null default '',
    sort_order integer not null default 0,
    status varchar(50) not null default 'EMPTY',
    format_override_json jsonb not null default '{}'::jsonb,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (draft_id, template_node_key)
);

create index idx_draft_node_draft_id on draft_node(draft_id);
create index idx_draft_node_sort_order on draft_node(draft_id, sort_order);
create index idx_draft_node_mapping_profile_id on draft_node(structure_mapping_profile_id);
