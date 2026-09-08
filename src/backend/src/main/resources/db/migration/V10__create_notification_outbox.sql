-- Snapshot notifications in the same transaction as their business event; deliver independently.
CREATE TABLE notification_outbox (
    event_id uuid PRIMARY KEY,
    event_key varchar(255) NOT NULL,
    channel varchar(30) NOT NULL,
    order_id integer NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    payload jsonb NOT NULL,
    status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','FAILED','SKIPPED')),
    attempts integer NOT NULL DEFAULT 0 CHECK(attempts>=0),
    available_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    last_error varchar(80),
    UNIQUE(event_key,channel)
);
CREATE INDEX notification_outbox_ready ON notification_outbox(available_at,created_at) WHERE status='PENDING';
