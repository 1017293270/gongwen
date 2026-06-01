alter table document_render_preview
    add column draft_id bigint references draft(id) on delete cascade;

create index idx_document_render_preview_draft
    on document_render_preview (draft_id, created_at desc)
    where draft_id is not null;
