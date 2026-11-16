-- Photos of the report elements. Files live on disk (volume), metadata here.
CREATE TABLE photo (
    id             BIGSERIAL PRIMARY KEY,
    report_item_id BIGINT       NOT NULL REFERENCES report_item (id) ON DELETE CASCADE,
    file_key       VARCHAR(64)  NOT NULL UNIQUE,
    caption        VARCHAR(500),
    position       INTEGER      NOT NULL,
    width          INTEGER      NOT NULL,
    height         INTEGER      NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    original_name  VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX photo_item_idx ON photo (report_item_id, position);

-- Main photo of the facade, shown on the PDF cover
ALTER TABLE report
    ADD CONSTRAINT report_cover_photo_fk FOREIGN KEY (cover_photo_id) REFERENCES photo (id) ON DELETE SET NULL;
