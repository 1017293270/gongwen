alter table draft
    add column template_version_id bigint references document_template_version(id);

create index idx_draft_template_version_id on draft(template_version_id);
