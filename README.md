# AIMS

Backend NestJS đã chuyển sang Java21 / Spring Boot3.5.16, modular monolith theo feature.
Angular giữ nguyên nguồn và các sửa đổi token đã được duyệt ở Module7/9/10.
Catalog, user/auth/admin, product/audit, cart/shipping, order, PayPal, VietQR, refunds và notifications
đã được triển khai. [Tiến độ/kiểm thử](MIGRATION_STATUS.md), [API contract](docs/api-contract.md).

Các feature được chia trực tiếp thành `controller`, `service`, `repository`, `entity` và `dto` để
tìm lớp theo tên quen thuộc của Spring. Các tích hợp đặc thù dùng package như `security`, `client`,
`gateway` và `provider`; xem [cấu trúc backend](docs/backend-structure.md).

## Chạy bằng Docker

Runtime chỉ dùng Supabase; PostgreSQL local, local profile và script tạo local DB đã được bỏ. File
`.env.supabase` quyền 0600, bị Git-ignore, chứa kết nối PostgreSQL cùng cấu hình
JWT/PayPal/VietQR/SendGrid. Không cần JDK, Maven, Node hoặc database trên host:

```sh
docker compose up --build
```

Muốn chạy nền và chờ cả backend/frontend healthy: `docker compose up -d --build --wait`.
Backend Spring production tại `http://localhost:3000`; Angular/Nginx tại
`http://localhost:4200`. Backend kết nối trực tiếp database Supabase với schema Flyway `aims_java`;
API catalog hiện trả 182 sản phẩm ACTIVE từ tổng 204 sản phẩm đã migrate. Compose không tạo service,
port hay volume database local.

Schema NestJS `public` không có Flyway history nên không được auto-baseline hoặc dùng đồng thời như
một writer thứ hai. `aims_java` là snapshot; ghi mới về sau từ NestJS không tự đồng bộ. File cấu hình
hiện bật SendGrid thật; PayPal/VietQR vẫn sandbox/development và VietQR test callback bị ép tắt.
Không commit hoặc in nội dung `.env.supabase`.

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

## Kiểm thử và Docker

```sh
cd src/backend
./mvnw -B test
./mvnw -B verify
cd ../..
python3 tools/verify-frontend.py
python3 tools/verify-compose.py
docker compose up -d --build --wait
```

Dockerfile backend multi-stage tự build JAR trong builder JDK21; image runtime chỉ chứa JRE21 và
JAR. Dockerfile frontend build Angular trong Node24 rồi chỉ chép `dist` vào Nginx unprivileged.
Hai runtime đều có healthcheck, filesystem read-only, `/tmp` tạm và dropped capabilities. Compose
chỉ khởi động backend port3000 và frontend port4200; database nằm ngoài Docker tại Supabase.
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

`.env.supabase` cũng ánh xạ cấu hình PayPal sandbox, VietQR development và SendGrid từ backend cũ.
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
