-- Matched to original TypeORM catalog metadata; see docs/module-1/typeorm-schema.sql.
-- Intentionally fail on existing unmanaged tables instead of silently adopting them.
CREATE TABLE products (
    product_id SERIAL NOT NULL,
    product_type varchar(20) NOT NULL,
    title varchar(255) NOT NULL,
    category varchar(45) NOT NULL,
    description text,
    barcode varchar(50) NOT NULL,
    length double precision,
    width double precision,
    height double precision,
    weight double precision NOT NULL,
    original_value numeric(12,2) NOT NULL,
    current_price numeric(12,2) NOT NULL,
    quantity_in_stock integer NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    image_url varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT "PK_a8940a4bf3b90bd7ac15c8f4dd9" PRIMARY KEY (product_id),
    CONSTRAINT "UQ_adfc522baf9d9b19cd7d9461b7e" UNIQUE (barcode),
    CONSTRAINT "CHK_127d8048f701f53190520ed16a" CHECK (quantity_in_stock >= 0),
    CONSTRAINT "CHK_a9104506eed0c02ff6d674d6a5" CHECK (current_price >= 0.3 * original_value AND current_price <= 1.5 * original_value)
);
CREATE TABLE media (
    product_id integer NOT NULL,
    publisher varchar(255),
    release_date date,
    language varchar(50),
    genre varchar(100),
    CONSTRAINT "PK_1fe69e256dfd757e9e7651c6bf5" PRIMARY KEY (product_id),
    CONSTRAINT "FK_1fe69e256dfd757e9e7651c6bf5" FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);
CREATE TABLE books (
    product_id integer NOT NULL,
    authors text NOT NULL,
    cover_type varchar(50) NOT NULL,
    num_pages integer,
    CONSTRAINT "PK_38bd9be46e6d774a3f56b0d3f14" PRIMARY KEY (product_id),
    CONSTRAINT "FK_38bd9be46e6d774a3f56b0d3f14" FOREIGN KEY (product_id) REFERENCES media(product_id) ON DELETE CASCADE
);
CREATE TABLE cds (
    product_id integer NOT NULL,
    artists text NOT NULL,
    CONSTRAINT "PK_52d7a03c9d4d868605644c3fc40" PRIMARY KEY (product_id),
    CONSTRAINT "FK_52d7a03c9d4d868605644c3fc40" FOREIGN KEY (product_id) REFERENCES media(product_id) ON DELETE CASCADE
);
CREATE TABLE cd_tracks (
    track_id SERIAL NOT NULL,
    title varchar(255) NOT NULL,
    length_seconds integer NOT NULL,
    product_id integer,
    CONSTRAINT "PK_e816048cecdeb59f09a2ba01345" PRIMARY KEY (track_id),
    CONSTRAINT "FK_31404a74b21031fa472a947bea6" FOREIGN KEY (product_id) REFERENCES cds(product_id) ON DELETE CASCADE
);
CREATE TABLE dvds (
    product_id integer NOT NULL,
    disc_type varchar(50) NOT NULL,
    director varchar(255) NOT NULL,
    runtime_minutes integer NOT NULL,
    subtitles text NOT NULL,
    CONSTRAINT "PK_d8dbdef0c3bc5cc009f705bc76f" PRIMARY KEY (product_id),
    CONSTRAINT "FK_d8dbdef0c3bc5cc009f705bc76f" FOREIGN KEY (product_id) REFERENCES media(product_id) ON DELETE CASCADE
);
CREATE TABLE newspapers (
    product_id integer NOT NULL,
    editor_in_chief varchar(255) NOT NULL,
    issue_number varchar(50),
    frequency varchar(50),
    issn varchar(50),
    sections text,
    CONSTRAINT "PK_f48c49378b969c79d09813725a0" PRIMARY KEY (product_id),
    CONSTRAINT "FK_f48c49378b969c79d09813725a0" FOREIGN KEY (product_id) REFERENCES media(product_id) ON DELETE CASCADE
);
