-- Imports the labeled BPMN corpus (dataset.csv + evaluation_data.csv, a table export of the base paper's GRIPL
-- database) into the current schema (V5). New ids are assigned by the DB and test cases are mapped to their new
-- dataset ids, so existing data is not touched. The imported test cases are BPMN-only (no process_text, text_labels
-- default to []). Runs in one transaction: on any error nothing is written.
--
-- Usage (Git Bash, from the repo root, backend started once so that the migrations are applied):
--   MSYS_NO_PATHCONV=1 docker exec gripl-postgres mkdir -p /tmp/import
--   for f in dataset.csv evaluation_data.csv import_dataset.sql; do docker cp dataset/$f gripl-postgres:/tmp/import/; done
--   MSYS_NO_PATHCONV=1 docker exec gripl-postgres psql -U postgres -d gripldb -v ON_ERROR_STOP=1 -f /tmp/import/import_dataset.sql
--
-- Note: running it twice imports the datasets twice.

BEGIN;

CREATE TEMP TABLE import_dataset (id int, name text, description text, created_at timestamptz, updated_at timestamptz);
CREATE TEMP TABLE import_testcase (id int, bpmn_xml text, expected_values jsonb, created_at timestamptz,
                                   updated_at timestamptz, name text, dataset_id int);

\copy import_dataset FROM '/tmp/import/dataset.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy import_testcase FROM '/tmp/import/evaluation_data.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

CREATE TEMP TABLE dataset_map (old_id int, new_id int);

WITH ins AS (
    INSERT INTO dataset (name, description, created_at, updated_at)
    SELECT name, description, created_at, updated_at FROM import_dataset ORDER BY id
    RETURNING id, name
)
INSERT INTO dataset_map SELECT d.id, ins.id FROM ins JOIN import_dataset d ON d.name = ins.name;

INSERT INTO evaluation_data (bpmn_xml, expected_values, created_at, updated_at, name, dataset_id)
SELECT t.bpmn_xml, t.expected_values, t.created_at, t.updated_at, t.name, m.new_id
FROM import_testcase t JOIN dataset_map m ON m.old_id = t.dataset_id
ORDER BY t.id;

SELECT d.id, d.name, count(e.id) AS testcases
FROM dataset d LEFT JOIN evaluation_data e ON e.dataset_id = d.id
GROUP BY d.id, d.name ORDER BY d.id;

COMMIT;
