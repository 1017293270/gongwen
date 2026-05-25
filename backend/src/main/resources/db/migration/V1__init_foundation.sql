create table app_metadata (
    id bigserial primary key,
    metadata_key varchar(100) not null unique,
    metadata_value varchar(500) not null,
    created_at timestamptz not null default now()
);

insert into app_metadata (metadata_key, metadata_value)
values ('schema_version', 'foundation-v1');
