create table quality_check_result (
    id uuid primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    status varchar(30) not null,
    export_blocked boolean not null default false,
    result_json jsonb not null,
    ai_trace_id uuid references ai_generation_trace(id),
    created_at timestamptz not null default now()
);

create index idx_quality_check_result_draft_id on quality_check_result(draft_id, created_at desc);
create index idx_quality_check_result_status on quality_check_result(status);
