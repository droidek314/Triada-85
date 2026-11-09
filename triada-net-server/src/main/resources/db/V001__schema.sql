-- TRIADA-NET, schema version 1 (M18). Times are milliseconds since 1970 (UTC).
-- Handles are upper case. A password is stored only as its PBKDF2 hash.

CREATE TABLE users (
    handle       TEXT PRIMARY KEY,
    password     TEXT NOT NULL,
    failures     INTEGER NOT NULL DEFAULT 0,
    locked_until INTEGER,
    banned       INTEGER NOT NULL DEFAULT 0,
    created      INTEGER NOT NULL
);

CREATE TABLE messages (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    board    TEXT NOT NULL,
    area     TEXT NOT NULL,
    author   TEXT NOT NULL,
    subject  TEXT NOT NULL,
    body     TEXT NOT NULL,
    time     INTEGER NOT NULL,
    reply_to INTEGER NOT NULL DEFAULT 0,
    hidden   INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX messages_by_area ON messages (board, area, hidden, id);

CREATE TABLE reports (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    message_id INTEGER NOT NULL,
    reporter   TEXT NOT NULL,
    time       INTEGER NOT NULL
);

CREATE TABLE mail (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    sender    TEXT NOT NULL,
    recipient TEXT NOT NULL,
    subject   TEXT NOT NULL,
    body      TEXT NOT NULL,
    time      INTEGER NOT NULL,
    seen      INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX mail_by_recipient ON mail (recipient, id);

CREATE TABLE files (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    board       TEXT NOT NULL,
    area        TEXT NOT NULL,
    name        TEXT NOT NULL,
    type        TEXT NOT NULL,
    description TEXT NOT NULL,
    data        BLOB NOT NULL,
    words       INTEGER NOT NULL,
    uploader    TEXT NOT NULL,
    status      TEXT NOT NULL,
    time        INTEGER NOT NULL
);
CREATE INDEX files_by_area ON files (board, area, status);

CREATE TABLE calls (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    board  TEXT NOT NULL,
    handle TEXT NOT NULL,
    time   INTEGER NOT NULL
);
CREATE INDEX calls_by_board ON calls (board, id);

CREATE TABLE one_liners (
    id     INTEGER PRIMARY KEY AUTOINCREMENT,
    board  TEXT NOT NULL,
    handle TEXT NOT NULL,
    text   TEXT NOT NULL,
    time   INTEGER NOT NULL
);

CREATE TABLE time_used (
    board   TEXT NOT NULL,
    who     TEXT NOT NULL,
    day     TEXT NOT NULL,
    seconds INTEGER NOT NULL,
    PRIMARY KEY (board, who, day)
);

CREATE TABLE banned_ips (
    ip TEXT PRIMARY KEY
);
