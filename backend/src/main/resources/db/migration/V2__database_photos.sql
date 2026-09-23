CREATE TABLE photo_objects (
 owner_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
 object_key text NOT NULL,
 content bytea NOT NULL CHECK(octet_length(content) <= 5242880),
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id, object_key)
);
