create unique index if not exists idx_draft_node_draft_id_id_unique
    on draft_node(draft_id, id);

alter table ai_paragraph_candidate
    drop constraint if exists ai_paragraph_candidate_target_node_id_fkey;

alter table ai_paragraph_candidate
    add constraint fk_ai_paragraph_candidate_target_node_draft
        foreign key (draft_id, target_node_id)
        references draft_node(draft_id, id)
        on delete set null (target_node_id);
