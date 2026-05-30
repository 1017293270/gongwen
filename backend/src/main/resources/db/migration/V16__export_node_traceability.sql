alter table export_record
    add column structure_mapping_profile_id bigint,
    add column structure_mapping_version integer,
    add column structure_profile_snapshot_json jsonb,
    add column mapping_profile_snapshot_json jsonb,
    add column formatting_snapshot_json jsonb,
    add column node_snapshot_json jsonb;

create index idx_export_record_structure_mapping_profile_id
    on export_record(structure_mapping_profile_id);
