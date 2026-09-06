# AIMS

Migration có kiểm soát từ NestJS sang Java Spring Boot, giữ nguyên Angular frontend.
MODULE 1 đã chuyển public product catalog; các module nghiệp vụ khác chưa được chuyển.
Tiến độ và điều kiện tiếp tục nằm trong [MIGRATION_STATUS.md](MIGRATION_STATUS.md).

## Cấu trúc

```text
src/frontend/       Angular giữ nguyên từ source (70 file, gồm lockfile)
src/backend/        Java 21 / Spring Boot / Maven Wrapper
docs/               API contract, rủi ro, manifest frontend, kết quả kiểm tra
tools/              Tạo cấu hình local và xác minh frontend
docker-compose.yml  PostgreSQL riêng cho AIMS local
AGENTS.md           Quy tắc checkpoint cho các lượt tiếp theo
```

## Yêu cầu

- JDK 21, Docker đang chạy, Python 3 để chạy công cụ kiểm tra.
- Maven được tải tự động qua Wrapper 3.3.4, cố định Maven 3.9.16; không cần cài Maven toàn máy.
- Spring Boot cố định 3.5.16, patch stable của nhánh yêu cầu và tương thích Java 21 theo
  [tài liệu Spring](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
- PostgreSQL 17.6 Alpine dùng chung cho Compose và Testcontainers; đây là lựa chọn local,
  chưa xác nhận phiên bản database triển khai cũ. Không cần tài khoản PayPal/VietQR/SendGrid.

Đặt `JAVA_HOME` trỏ tới JDK 21 trước khi dùng Wrapper. Maven Enforcer từ chối JDK khác 21.
Trên macOS có JDK 21 đã cài: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.
Quy trình PHASE 0 dùng JDK tạm đã kiểm tra SHA-256; xem [bằng chứng](docs/phase-0-validation.md).

## Chạy local

Từ thư mục AIMS:

```sh
python3 tools/init-local-env.py
docker compose up -d --wait postgres
set -a
. ./.env
set +a
cd src/backend
./mvnw spring-boot:run
```

Kiểm tra ở terminal khác:

```sh
curl --fail http://localhost:3000/actuator/health
```

Khi PostgreSQL hoạt động, response là `{"status":"UP"}`. Profile mặc định `local` bind backend
127.0.0.1:3000, kết nối `127.0.0.1:55432/aims_local` với user `aims_local`.
Password ngẫu nhiên được tạo trong `.env` ignored, quyền 0600, không in ra console và không ghi đè
nếu file đã tồn tại. Spring không tự đọc `.env`, nên phải export như hướng dẫn. Không source `.env`
của project NestJS. Compose có volume riêng và chỉ publish PostgreSQL ra loopback.

Dừng backend bằng Ctrl+C; `docker compose stop` dừng database và giữ dữ liệu.
Để chạy JAR sau build: `java -jar target/aims-backend-0.0.1-SNAPSHOT.jar` trong src/backend,
vẫn dùng JDK 21 và biến môi trường local đã export.

| Biến / cấu hình | Ý nghĩa PHASE 0 |
| --- | --- |
| JAVA_HOME | JDK 21 |
| AIMS_LOCAL_DB_PASSWORD | Password database local được sinh tự động, không có giá trị cố định trong Git |
| PORT | Port backend, mặc định 3000 |
| MAVEN_USER_HOME | Tùy chọn vị trí cache Maven Wrapper |
| DOCKER_HOST | Chỉ đặt nếu Testcontainers không tìm được Docker socket; ví dụ Docker Desktop macOS: unix:///Users/abc/.docker/run/docker.sock |
| spring.jpa.hibernate.ddl-auto | validate, không create/update |
| spring.flyway.clean-disabled | true |
| spring.flyway.baseline-on-migrate | false |

Không có production profile hay cấu hình gateway ở checkpoint này. JWT và CORS cho các feature
khác sẽ được chuyển ở module 3, gateway/email ở module 9/10/12, image backend/CI/deployment ở module 13.
Spring Security mở health và public catalog GET/HEAD; API quản trị chưa được mở.

Public catalog giữ nguyên query/JSON của Angular:

```sh
curl 'http://localhost:3000/api/products?keyword=book&mediaTypes=BOOK,CD&minPrice=0'
curl http://localhost:3000/api/products/random
curl http://localhost:3000/api/products/1
```

Database local ban đầu không có sản phẩm: search/random trả `[]`, detail chưa tồn tại trả404.
Catalog có CORS tương thích localhost/Vercel/ALLOWED_ORIGINS và Cache-Control no-store.

## Kiểm thử

Docker phải chạy. Tests dùng PostgreSQL Testcontainer mới, tách khỏi database Compose, không cần
`.env`. Thiếu Docker thì test phải thất bại, không được skip.

```sh
cd src/backend
./mvnw -B -Dtest=FoundationTest test
./mvnw -B -Dtest=CatalogIntegrationTest,ProductServiceTest test
./mvnw -B test
./mvnw -B verify
```

Suite kiểm tra application context, HTTP thật, MockMvc health, phạm vi endpoint được mở,
Flyway apply/validate/re-run và cấu hình không phá hủy schema. V1 chỉ tạo lịch sử Flyway;
V2 tạo 7 bảng catalog, giữ constraint/index của TypeORM. Test MODULE 1 đối chiếu JSON với mẫu
chạy từ TypeScript gốc và so sánh metadata schema trên PostgreSQL.

Từ root, xác minh frontend với source read-only:

```sh
python3 tools/verify-frontend.py
```

Có thể dùng `--source /path/to/ISD.20252-25`. Công cụ kiểm tra tập file và từng SHA-256 so với cả
source lẫn manifest đã commit. Không format/cài dependency/chạy build trong project nguồn.
Frontend build đầy đủ nằm ở MODULE 13. Hiện chỉ catalog được chuyển; checkout/auth/payment chưa có.
Frontend vẫn giữ nguyên API_BASE_URL: localhost/127.0.0.1 dùng port 3000, host khác gọi Render cũ.

## Tài liệu và checkpoint

- [API contract](docs/api-contract.md): 44 route NestJS, headers, payloads, status/errors, Angular consumers.
- [Rủi ro cần quyết định](docs/migration-risks.md): schema chưa có dump, quyền truy cập order/payment,
  JWT fallback, user seeding, callback và concurrency.
- [Quy tắc lâu dài](AGENTS.md): mỗi lượt đúng một checkpoint, test/build/review/commit/push rồi dừng.
- [Maven Wrapper chính thức](https://maven.apache.org/tools/wrapper/index.html).

Source gốc `../ISD.20252-25` là read-only. Không thay đổi frontend để thích ứng với Java.
Chỉ tiếp tục MODULE 2 khi người dùng gửi `TIẾP TỤC MODULE 2`.
