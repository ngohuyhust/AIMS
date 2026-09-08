-- Durable action reservation prevents approve/cancel/refund races and repeated stock restoration.
CREATE TABLE order_lifecycle_operations (
    order_id integer PRIMARY KEY REFERENCES orders(order_id) ON DELETE CASCADE,
    action varchar(10) NOT NULL CHECK (action IN ('CANCEL','REJECT')),
    transaction_id integer REFERENCES payment_transactions(transaction_id),
    refund_method varchar(45),
    status varchar(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);
