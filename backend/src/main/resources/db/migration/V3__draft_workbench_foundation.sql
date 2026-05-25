create table document_type (
    code varchar(50) primary key,
    name varchar(100) not null,
    status varchar(30) not null default 'ACTIVE',
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into document_type (code, name, status, sort_order)
values
    ('NOTICE', '通知', 'ACTIVE', 1),
    ('REQUEST', '请示', 'ACTIVE', 2),
    ('REPORT', '报告', 'ACTIVE', 3);

create table draft (
    id bigserial primary key,
    document_type_code varchar(50) not null references document_type(code),
    title varchar(300) not null,
    status varchar(30) not null default 'DRAFT',
    template_id bigint,
    created_by bigint,
    tenant_id bigint,
    department_id bigint,
    review_status varchar(30),
    version_no integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table draft_block (
    id bigserial primary key,
    draft_id bigint not null references draft(id) on delete cascade,
    block_type varchar(50) not null,
    content text not null default '',
    sort_order integer not null default 0,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_draft_document_type_code on draft(document_type_code);
create index idx_draft_block_draft_id on draft_block(draft_id);
create index idx_draft_block_sort_order on draft_block(draft_id, sort_order);
