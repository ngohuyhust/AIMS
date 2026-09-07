-- Exact original TypeORM product_logs metadata; existing product/user schema is unchanged.
CREATE TABLE product_logs (
    log_id SERIAL NOT NULL,
    action_type varchar(20) NOT NULL,
    changed_fields jsonb,
    performed_by varchar(100) NOT NULL DEFAULT 'SYSTEM',
    reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    product_id integer,
    CONSTRAINT "PK_89d905c5f8357738d88f606a4c6" PRIMARY KEY (log_id),
    CONSTRAINT "FK_3508cbbf6e40f50ce23a3255708" FOREIGN KEY (product_id)
        REFERENCES products(product_id) ON DELETE SET NULL ON UPDATE NO ACTION
);
