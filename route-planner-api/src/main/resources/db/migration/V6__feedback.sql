-- What visitors and users tell us from the feedback form, including what they'd pay.
-- Kept even when emailing it fails, so nothing is lost.
CREATE TABLE feedback (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          text,
    email         text,
    message       text        NOT NULL CHECK (length(message) <= 4000),
    price_band    text        NOT NULL CHECK (price_band IN ('nothing', 'under_10', 'from_10_to_20', 'from_20_to_50', 'from_50_to_100', 'not_sure')),
    price_amount  numeric(10, 2) CHECK (price_amount >= 0),     -- an exact monthly amount, when they gave one
    emailed_at    timestamptz,                                  -- null: not sent (mail not set up, or it failed)
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX feedback_created_at_idx ON feedback (created_at);
