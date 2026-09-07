-- Original TypeORM metadata, generated offline.
CREATE TABLE "product_logs" ("log_id" SERIAL NOT NULL, "action_type" character varying(20) NOT NULL, "changed_fields" jsonb, "performed_by" character varying(100) NOT NULL DEFAULT 'SYSTEM', "reason" text, "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "product_id" integer, CONSTRAINT "PK_89d905c5f8357738d88f606a4c6" PRIMARY KEY ("log_id"));
ALTER TABLE "product_logs" ADD CONSTRAINT "FK_3508cbbf6e40f50ce23a3255708" FOREIGN KEY ("product_id") REFERENCES "products"("product_id") ON DELETE SET NULL ON UPDATE NO ACTION;
