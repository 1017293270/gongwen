create table ai_generation_trace (
    id uuid primary key,
    draft_id bigint not null references draft(id),
    task_type varchar(64) not null,
    provider varchar(64) not null,
    model_name varchar(128) not null,
    status varchar(32) not null,
    prompt_version varchar(32) not null,
    input_summary text not null,
    output_summary text,
    error_code varchar(128),
    error_message text,
    latency_ms bigint not null,
    tenant_id bigint,
    department_id bigint,
    version bigint not null default 0,
    created_at timestamp not null default now()
);

create index idx_ai_generation_trace_draft_id on ai_generation_trace(draft_id);
create index idx_ai_generation_trace_task_type on ai_generation_trace(task_type);
