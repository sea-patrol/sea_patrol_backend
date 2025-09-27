CREATE TABLE users (
    id UUID NOT NULL,
    username VARCHAR_IGNORECASE NOT NULL UNIQUE,
    password TEXT NOT NULL,
    role TEXT NOT NULL,
    locked BOOLEAN DEFAULT FALSE,
    created_on TIMESTAMP,
    updated_on TIMESTAMP,
    CONSTRAINT user_pkey PRIMARY KEY (id)
);