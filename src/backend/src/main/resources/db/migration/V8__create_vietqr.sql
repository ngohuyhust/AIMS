-- Original TypeORM metadata, generated offline.
CREATE TABLE "vietqr_transactions" ("vietqr_transaction_id" SERIAL NOT NULL, "order_id" integer NOT NULL, "amount" numeric(12,2) NOT NULL, "content" character varying(23) NOT NULL, "qr_code" text, "qr_link" text, "transaction_id_ref" character varying(100), "transaction_ref_id" character varying(100), "expired_at" TIMESTAMP WITH TIME ZONE NOT NULL, "paid_at" TIMESTAMP WITH TIME ZONE, "status" character varying(50) NOT NULL DEFAULT 'PENDING', "raw_callback" jsonb, "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "transaction_id" integer, CONSTRAINT "REL_29d924c8509092e8cc90a1e8af" UNIQUE ("transaction_id"), CONSTRAINT "PK_12d32af8662ac9fde68dac661e8" PRIMARY KEY ("vietqr_transaction_id"));
ALTER TABLE "vietqr_transactions" ADD CONSTRAINT "FK_29d924c8509092e8cc90a1e8afb" FOREIGN KEY ("transaction_id") REFERENCES "payment_transactions"("transaction_id") ON DELETE CASCADE ON UPDATE NO ACTION;

-- One authenticated bank receipt can settle only one payment, including concurrent callbacks.
CREATE TABLE vietqr_receipts (
    bank_account varchar(100) NOT NULL,
    bank_transaction_id varchar(100) NOT NULL,
    transaction_id integer NOT NULL UNIQUE REFERENCES payment_transactions(transaction_id) ON DELETE CASCADE,
    PRIMARY KEY (bank_account, bank_transaction_id)
);
