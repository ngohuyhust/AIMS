-- Generated offline from original TypeORM 0.3.29 user entity metadata.
CREATE TABLE "roles" ("role_id" SERIAL NOT NULL, "name" character varying(50) NOT NULL, CONSTRAINT "UQ_648e3f5447f725579d7d4ffdfb7" UNIQUE ("name"), CONSTRAINT "PK_09f4c8130b54f35925588a37b6a" PRIMARY KEY ("role_id"));
CREATE TABLE "users" ("user_id" SERIAL NOT NULL, "email" character varying(100) NOT NULL, "password_hash" character varying(255) NOT NULL, "full_name" character varying(255) NOT NULL, "phone_number" character varying(20), "status" character varying(20) NOT NULL DEFAULT 'ACTIVE', "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "updated_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), CONSTRAINT "UQ_97672ac88f789774dd47f7c8be3" UNIQUE ("email"), CONSTRAINT "PK_96aac72f1574b88752e9fb00089" PRIMARY KEY ("user_id"));
CREATE TABLE "user_audit_logs" ("log_id" SERIAL NOT NULL, "action" character varying(100) NOT NULL, "description" text, "performed_by" character varying(50), "created_at" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "user_id" integer, CONSTRAINT "PK_ba722bb69ea8d67ad7aa62af519" PRIMARY KEY ("log_id"));
CREATE TABLE "users_roles" ("user_id" integer NOT NULL, "role_id" integer NOT NULL, CONSTRAINT "PK_c525e9373d63035b9919e578a9c" PRIMARY KEY ("user_id", "role_id"));
ALTER TABLE "user_audit_logs" ADD CONSTRAINT "FK_b45c5e4d92b4f9be4278f340017" FOREIGN KEY ("user_id") REFERENCES "users"("user_id") ON DELETE SET NULL ON UPDATE NO ACTION;
CREATE INDEX "IDX_e4435209df12bc1f001e536017" ON "users_roles" ("user_id") ;
CREATE INDEX "IDX_1cf664021f00b9cc1ff95e17de" ON "users_roles" ("role_id") ;
ALTER TABLE "users_roles" ADD CONSTRAINT "FK_e4435209df12bc1f001e5360174" FOREIGN KEY ("user_id") REFERENCES "users"("user_id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "users_roles" ADD CONSTRAINT "FK_1cf664021f00b9cc1ff95e17de4" FOREIGN KEY ("role_id") REFERENCES "roles"("role_id") ON DELETE NO ACTION ON UPDATE NO ACTION;
