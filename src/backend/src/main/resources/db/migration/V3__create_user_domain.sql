-- Original TypeORM user metadata: names, nullability and referential actions retained.
CREATE TABLE roles (
    role_id SERIAL NOT NULL,
    name varchar(50) NOT NULL,
    CONSTRAINT "PK_09f4c8130b54f35925588a37b6a" PRIMARY KEY (role_id),
    CONSTRAINT "UQ_648e3f5447f725579d7d4ffdfb7" UNIQUE (name)
);
CREATE TABLE users (
    user_id SERIAL NOT NULL,
    email varchar(100) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name varchar(255) NOT NULL,
    phone_number varchar(20),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT "PK_96aac72f1574b88752e9fb00089" PRIMARY KEY (user_id),
    CONSTRAINT "UQ_97672ac88f789774dd47f7c8be3" UNIQUE (email)
);
CREATE TABLE user_audit_logs (
    log_id SERIAL NOT NULL,
    action varchar(100) NOT NULL,
    description text,
    performed_by varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    user_id integer,
    CONSTRAINT "PK_ba722bb69ea8d67ad7aa62af519" PRIMARY KEY (log_id),
    CONSTRAINT "FK_b45c5e4d92b4f9be4278f340017" FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE NO ACTION
);
CREATE TABLE users_roles (
    user_id integer NOT NULL,
    role_id integer NOT NULL,
    CONSTRAINT "PK_c525e9373d63035b9919e578a9c" PRIMARY KEY (user_id, role_id),
    CONSTRAINT "FK_e4435209df12bc1f001e5360174" FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT "FK_1cf664021f00b9cc1ff95e17de4" FOREIGN KEY (role_id)
        REFERENCES roles(role_id) ON DELETE NO ACTION ON UPDATE NO ACTION
);
CREATE INDEX "IDX_e4435209df12bc1f001e536017" ON users_roles (user_id);
CREATE INDEX "IDX_1cf664021f00b9cc1ff95e17de" ON users_roles (role_id);

-- One-time role-only seed. Never create accounts or overwrite credentials/status/roles.
INSERT INTO roles (name) VALUES ('ADMIN'), ('PRODUCT_MANAGER'), ('STAFF')
ON CONFLICT (name) DO NOTHING;
