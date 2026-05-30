alter table export_record
    add column template_version_id bigint;

create index idx_export_record_template_version_id on export_record(template_version_id);
create index idx_export_record_department_id on export_record(department_id);
