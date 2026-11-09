-- TRIADA-NET, schema version 2 (M19): which pieces of the boards' starting
-- content (posts, files) are already in the database, so a restart adds
-- nothing twice. A key is e.g. "post 1-85-01 NEWS 1985-03-02T14:10 KVOLKOV".

CREATE TABLE seeds (
    key TEXT PRIMARY KEY
);
