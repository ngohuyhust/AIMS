# AIMS

Backend NestJS đã chuyển sang Java21 / Spring Boot3.5.16, modular monolith theo feature.
Angular giữ nguyên nguồn và các sửa đổi token đã được duyệt ở Module7/9/10.
Catalog, user/auth/admin, product/audit, cart/shipping, order, PayPal, VietQR, refunds và notifications
đã được triển khai. [Tiến độ/kiểm thử](MIGRATION_STATUS.md), [API contract](docs/api-contract.md).

## Chạy local

Cần JDK21, Docker, Python3; frontend dùng Node24 và lockfile hiện tại.
Maven Wrapper cố định Maven3.9.16; không cần cài Maven toàn máy.

```sh
python3 tools/init-local-env.py
docker compose up -d --wait postgres
set -a
. ./.env
set +a
cd src/backend
./mvnw spring-boot:run
```

Backend mặc định bind `127.0.0.1:3000`; PostgreSQL riêng tại `127.0.0.1:55432/aims_local`.
Công cụ init sinh password/JWT ngẫu nhiên, giữ cấu hình có sẵn; `.env` không được commit.
Không đọc `.env` của ISD, không tự kết nối database cũ. Không seed/reset tài khoản mặc định.
Health: `curl --fail http://localhost:3000/actuator/health` trả `{"status":"UP"}`.
Database mới có role, chưa có user/sản phẩm; initial ADMIN cần được cấp qua quy trình trong
[tài liệu triển khai](docs/deployment.md), không dùng tài khoản mặc định cũ.

Frontend, chạy từ root ở terminal khác:

```sh
cd src/frontend
npm ci
npm start
```

Mở `http://localhost:4200`. Không chạy build/install trong project ISD nguồn.
Frontend trên host khác localhost vẫn gọi `https://isd-20252-25.onrender.com`; muốn đổi sang domain
backend khác cần người dùng cho phép sửa cấu hình frontend. Không tự thay URL hoặc chuyển traffic.

## Kiểm thử và Docker

```sh
cd src/backend
./mvnw -B test
./mvnw -B verify
cd ../..
python3 tools/verify-frontend.py
docker compose --profile application up -d --build --wait
```

Image runtime lấy JAR **đã verify**, chạy JRE21 với UID10001, healthcheck, filesystem read-only và
`/tmp` tạm trong Compose. Build lại JAR trước mỗi image build. Compose mặc định chỉ bật PostgreSQL;
profile `application` thêm backend tại port3000 (dừng backend chạy bằng Maven trước để tránh trùng port).
`docker compose --profile application stop` giữ dữ liệu; không dùng `down -v` với dữ liệu cần giữ.

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
Email và callback thử VietQR vẫn tắt để việc khởi động không tự gọi provider hoặc gửi thư thật.

## Quy tắc nghiệp vụ đã chốt

- Quota/audit PM dùng email JWT, vẫn yêu cầu `x-manager-id`; tiền dùng BigDecimal/HALF_UP.
- Order detail/delivery và tạo/xác nhận thanh toán yêu cầu capability của đơn.
- Đang/đã thanh toán khóa sửa giao hàng; kiểm tra số tiền, callback lặp không cập nhật lần nữa.
- Refund và hủy đơn đã thanh toán chỉ PRODUCT_MANAGER; hoàn VietQR là xác nhận chuyển khoản thủ công.
- Email lưu outbox trong transaction; gửi riêng, mặc định tắt. Bật gửi sẽ xử lý cả backlog.

Source `../ISD.20252-25` chỉ đọc, commit `c7c022e33f100937cd0f072c3666fd0e26754d8e`.
[AGENTS.md](AGENTS.md) quy định checkpoint; [rủi ro](docs/migration-risks.md) ghi các giới hạn còn lại.
