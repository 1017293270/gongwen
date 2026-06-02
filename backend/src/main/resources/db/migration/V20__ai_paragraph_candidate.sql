create table ai_paragraph_candidate (
    id bigserial primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    target_node_id bigint references draft_node(id) on delete set null,
    target_node_role varchar(80) not null default '',
    target_node_title varchar(255) not null default '',
    outline_trace_id uuid,
    paragraph_trace_id uuid,
    section_index integer not null,
    heading varchar(500) not null default '',
    points_json jsonb not null default '[]'::jsonb,
    instruction_summary varchar(1000) not null default '',
    candidate_text text not null default '',
    candidate_text_digest varchar(128) not null default '',
    status varchar(40) not null,
    error_code varchar(120) not null default '',
    error_message varchar(1000) not null default '',
    accepted_at timestamptz,
    accepted_by bigint references app_user(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ai_paragraph_candidate_draft_section
    on ai_paragraph_candidate(draft_id, section_index, id);

create index idx_ai_paragraph_candidate_target_node
    on ai_paragraph_candidate(target_node_id);
