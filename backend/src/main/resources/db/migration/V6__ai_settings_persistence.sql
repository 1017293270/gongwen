create table ai_provider_settings (
    id bigint primary key,
    provider varchar(64) not null,
    deepseek_enabled boolean not null default false,
    deepseek_base_url varchar(512) not null,
    deepseek_model varchar(128) not null,
    deepseek_api_key_secret text,
    deepseek_timeout_seconds integer not null default 60,
    tenant_id bigint,
    department_id bigint,
    version bigint not null default 0,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);
