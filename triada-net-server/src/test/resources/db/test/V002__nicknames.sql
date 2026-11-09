-- a test upgrade: every caller gets a nickname
ALTER TABLE users ADD COLUMN nickname TEXT NOT NULL DEFAULT 'COMRADE';
