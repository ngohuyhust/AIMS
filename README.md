# AIMS

Backend NestJS đã chuyển sang Java21 / Spring Boot3.5.16, modular monolith theo feature.
Angular giữ nguyên nguồn và các sửa đổi token đã được duyệt ở Module7/9/10.
Catalog, user/auth/admin, product/audit, cart/shipping, order, PayPal, VietQR, refunds và notifications
đã được triển khai. [Tiến độ/kiểm thử](MIGRATION_STATUS.md), [API contract](docs/api-contract.md).

Các feature được chia trực tiếp thành `controller`, `service`, `repository`, `entity` và `dto` để
tìm lớp theo tên quen thuộc của Spring. Các tích hợp đặc thù dùng package như `security`, `client`,
`gateway` và `provider`; xem [cấu trúc backend](docs/backend-structure.md).

## Chạy local bằng Docker

Chỉ cần Docker và Python3 để tạo secret local lần đầu. Không cần cài JDK hoặc Maven trên máy host;
Dockerfile tự build Spring Boot bằng JDK21 và Maven Wrapper rồi tạo runtime image JRE21.

```sh
python3 tools/init-local-env.py
docker compose up --build
```

Sau lần tạo `.env` đầu tiên, chỉ cần chạy `docker compose up --build`. Muốn chạy nền và chờ cả
PostgreSQL/backend/frontend healthy: `docker compose up -d --build --wait`.

Backend mặc định bind `127.0.0.1:3000`; PostgreSQL riêng tại `127.0.0.1:55432/aims_local`.
Cùng lệnh đó build Angular bằng Node24 rồi phục vụ static bằng Nginx unprivileged tại
`http://localhost:4200`; route Angular được fallback về `index.html`.
Công cụ init sinh password/JWT ngẫu nhiên, giữ cấu hình có sẵn; `.env` không được commit.
Không đọc `.env` của ISD, không tự kết nối database cũ. Không seed/reset tài khoản mặc định.
Health: `curl --fail http://localhost:3000/actuator/health` trả `{"status":"UP"}`.
Database mới có role, chưa có user/sản phẩm; initial ADMIN cần được cấp qua quy trình trong
[tài liệu triển khai](docs/deployment.md), không dùng tài khoản mặc định cũ.

Nếu cần Angular dev server/hot reload thay vì container static, chạy riêng:

```sh
cd src/frontend
npm ci
npm start
```

Khi dùng Compose không cần chạy các lệnh frontend riêng. Không chạy build/install trong project ISD
nguồn.
Frontend trên host khác localhost vẫn gọi `https://isd-20252-25.onrender.com`; muốn đổi sang domain
backend khác cần người dùng cho phép sửa cấu hình frontend. Không tự thay URL hoặc chuyển traffic.

### Chạy với dữ liệu Supabase của NestJS

Workspace đã có file `.env.supabase` quyền0600, bị Git-ignore, ánh xạ kết nối PostgreSQL và cấu hình
PayPal/VietQR/SendGrid cũ sang Spring. Dừng stack local rồi chạy stack Supabase:

```sh
docker compose stop
docker compose -f compose.supabase.yml up --build
```

Stack này chỉ chạy Spring backend và Angular frontend; không khởi động PostgreSQL local. Backend kết
nối trực tiếp cùng database Supabase nhưng dùng schema Flyway `aims_java`, vì schema `public` của
NestJS không có Flyway history và không được cho phép auto-baseline. Snapshot hiện có1.521 dòng/19
bảng; API catalog trả182 sản phẩm ACTIVE từ tổng204 sản phẩm. Frontend vẫn mở tại
`http://localhost:4200` và gọi Spring tại localhost3000.

`NOTIFICATIONS_ENABLED=true` trong file local hiện tại nên thao tác nghiệp vụ mới có thể gửi email
thật qua SendGrid; PayPal/VietQR vẫn là sandbox/development và VietQR test callback bị ép tắt.
Không commit hoặc in nội dung `.env.supabase`. Stack `aims_java` là snapshot, không tự đồng bộ các
ghi mới về sau từ NestJS `public`.

## Kiểm thử và Docker

```sh
cd src/backend
./mvnw -B test
./mvnw -B verify
cd ../..
python3 tools/verify-frontend.py
python3 tools/verify-compose.py
python3 tools/verify-supabase-compose.py
docker compose up -d --build --wait
```

Dockerfile backend multi-stage tự build JAR trong builder JDK21; image runtime chỉ chứa JRE21 và
JAR. Dockerfile frontend build Angular trong Node24 rồi chỉ chép `dist` vào Nginx unprivileged.
Hai runtime đều có healthcheck, filesystem read-only, `/tmp` tạm và dropped capabilities. Compose
mặc định khởi động PostgreSQL, backend port3000 và frontend port4200. `docker compose stop` giữ dữ
liệu; không dùng `down -v` với dữ liệu cần giữ.
`mvnw test/verify` vẫn là bước kiểm thử bắt buộc trước phát hành; image build chỉ package với test
được bỏ qua vì suite Testcontainers đã chạy riêng.

Kiểm tra image độc lập, không đụng database Compose:

```sh
python3 tools/smoke-container.py --image aims-backend:local
```

Script tạo database/network ngẫu nhiên rồi tự dọn tài nguyên của chính nó. Testcontainers bắt buộc
PostgreSQL thật qua Docker, không skip khi thiếu Docker, không H2, không gọi gateway/email thật.
Frontend: `npm test -- --watch=false` và `npm run build` trong `src/frontend`.

CI `.github/workflows/ci.yml` chạy Maven verify/Testcontainers, Docker smoke và Angular test/build;
đối chiếu cả70 file nguồn từ commit ISD được bảo tồn trong Git bằng `verify-frontend.py --source-ref`.
CI chỉ kiểm tra/build, không tự deploy hoặc publish image. [Biến môi trường và rollout](docs/deployment.md).

Database Supabase cũ có thể được diễn tập không ghi bằng `python3 tools/rehearse-legacy-data.py`.
Migration có kiểm soát dùng schema riêng `aims_java`; xem
[hướng dẫn Supabase](docs/legacy-supabase-migration.md). Không khởi động NestJS chỉ để kiểm tra DB
vì cấu hình TypeORM cũ bật `synchronize: true`.

Trên workspace đã migration, backend snapshot có thể chạy bằng file local ignored quyền 0600:

```sh
docker run -d --name aims-supabase-api --env-file .env.supabase \
  -p 127.0.0.1:3000:3000 --read-only --tmpfs /tmp --cap-drop ALL \
  --security-opt no-new-privileges aims-backend:local
docker stop aims-supabase-api
docker start aims-supabase-api
```

File này cũng ánh xạ cấu hình PayPal sandbox, VietQR development và SendGrid từ backend cũ.
Theo xác nhận của người dùng, workspace hiện đặt `NOTIFICATIONS_ENABLED=true`: các sự kiện đơn hàng
mới sẽ gửi email thật qua SendGrid như NestJS. Callback thử VietQR vẫn tắt.
SendGrid không tham gia đăng nhập; contract gốc không có đăng ký công khai hoặc xác minh email.

## Quy tắc nghiệp vụ đã chốt

- Quota/audit PM dùng email JWT, vẫn yêu cầu `x-manager-id`; tiền dùng BigDecimal/HALF_UP.
- Order detail/delivery và tạo/xác nhận thanh toán yêu cầu capability của đơn.
- Đang/đã thanh toán khóa sửa giao hàng; kiểm tra số tiền, callback lặp không cập nhật lần nữa.
- Refund và hủy đơn đã thanh toán chỉ PRODUCT_MANAGER; hoàn VietQR là xác nhận chuyển khoản thủ công.
- Email lưu outbox trong transaction; gửi riêng, mặc định tắt. Bật gửi sẽ xử lý cả backlog.

Source `../ISD.20252-25` chỉ đọc, commit `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
[AGENTS.md](AGENTS.md) quy định checkpoint; [rủi ro](docs/migration-risks.md) ghi các giới hạn còn lại.
