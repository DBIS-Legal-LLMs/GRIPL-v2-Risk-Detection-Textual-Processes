-- A test case represents one process and can hold a BPMN model, a textual process description, or both
-- (paired). Existing test cases stay BPMN-only.
alter table evaluation_data alter column bpmn_xml drop not null;

alter table evaluation_data add column process_text text;

-- Labeled text passages: every activity in the text is labeled, critical ones are flagged (see TextLabel)
alter table evaluation_data add column text_labels jsonb not null default '[]'::jsonb;

alter table evaluation_data
    add constraint evaluation_data_has_modality check (bpmn_xml is not null or process_text is not null);
