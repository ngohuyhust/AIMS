INSERT INTO products (product_id,product_type,title,category,description,barcode,weight,original_value,current_price,quantity_in_stock,status,created_at,updated_at) VALUES
(1,'BOOK','Alpha Book','Literature','A story','BOOK-1',1.25,100000,30000,5,'ACTIVE','2026-01-02T03:04:05.000Z','2026-01-02T03:04:05.123Z'),
(2,'CD','Beta Album','Music',NULL,'CD-2',0.2,100000,150000,0,'ACTIVE','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(3,'DVD','Gamma Film','Cinema',NULL,'DVD-3',0.3,100000,50000,7,'ACTIVE','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(4,'NEWSPAPER','Delta News','Press',NULL,'NEWS-4',0.1,100000,40000,8,'ACTIVE','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(5,'BOOK','Inactive Book','BOOK',NULL,'BOOK-5',1,100000,100000,2,'DEACTIVATED','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(6,'BOOK','Deleted Book','BOOK',NULL,'BOOK-6',1,100000,100000,0,'DELETED','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(7,'BOOK','Missing Detail','BOOK',NULL,'BOOK-7',1,100000,100000,1,'ACTIVE','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z'),
(8,'UNKNOWN','Unknown Type','Other',NULL,'OTHER-8',1,100000,100000,1,'ACTIVE','2026-01-02T03:04:05Z','2026-01-02T03:04:05Z');
INSERT INTO media (product_id,publisher,release_date,language,genre) VALUES
(1,'Publisher One','2024-02-29','Vietnamese','Fiction'),(2,'Record Label',NULL,NULL,'Jazz'),
(3,'Film Studio','2023-01-01','English','Drama'),(4,'News Publisher','2026-01-01',NULL,NULL);
INSERT INTO books (product_id,authors,cover_type,num_pages) VALUES (1,'Author Needle','PAPERBACK',123);
INSERT INTO cds (product_id,artists) VALUES (2,'Artist Needle');
INSERT INTO cd_tracks (track_id,product_id,title,length_seconds) VALUES (21,2,'Track Needle One',123),(22,2,'Track Needle Two',234);
INSERT INTO dvds (product_id,disc_type,director,runtime_minutes,subtitles) VALUES (3,'Blu-ray','Director Needle',90,'Vietnamese');
INSERT INTO newspapers (product_id,editor_in_chief,sections) VALUES (4,'Editor Needle','Science Needle');
