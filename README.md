# AIMS — Internet Media Store

AIMS là hệ thống thương mại điện tử dành cho các sản phẩm media vật lý như sách, CD, DVD và báo.
Ứng dụng cung cấp đầy đủ luồng mua hàng cho khách, quản trị người dùng, quản lý sản phẩm và xử lý
đơn hàng trên một giao diện web thống nhất.

Backend được xây dựng bằng Spring Boot theo kiến trúc modular monolith. Frontend là ứng dụng Angular
độc lập, giao tiếp với backend qua REST API. Dữ liệu được lưu trên PostgreSQL/Supabase và quản lý
schema bằng Flyway.

## Chức năng chính

### Khách hàng

- Xem, tìm kiếm và lọc sản phẩm theo loại, danh mục và khoảng giá.
- Xem chi tiết sách, CD, DVD và báo đang kinh doanh.
- Quản lý giỏ hàng, kiểm tra tồn kho và tính phí vận chuyển.
- Đặt hàng không cần tài khoản; mỗi đơn được bảo vệ bằng access token riêng.
- Thanh toán qua PayPal hoặc VietQR.
- Xem trạng thái, hóa đơn và hủy đơn theo quy tắc nghiệp vụ.

### Quản trị viên

- Đăng nhập bằng email và mật khẩu.
- Tạo tài khoản nội bộ, khóa/mở khóa tài khoản và đặt lại mật khẩu.
- Gán vai trò và theo dõi nhật ký thay đổi người dùng.

### Product Manager

- Tạo, cập nhật, điều chỉnh tồn kho và ngừng kinh doanh sản phẩm.
- Theo dõi lịch sử thay đổi sản phẩm.
- Duyệt, từ chối hoặc hủy đơn hàng.
- Xử lý hoàn tiền PayPal và xác nhận hoàn tiền VietQR.

### Nền tảng

- Xác thực stateless bằng Spring Security và JWT.
- Phân quyền theo vai trò `ADMIN` và `PRODUCT_MANAGER`.
- Gửi email qua SendGrid bằng transactional outbox.
- Health check phục vụ giám sát container.
- Kiểm thử tích hợp với PostgreSQL thật thông qua Testcontainers.

## Kiến trúc

```text
┌──────────────────┐       REST/JSON       ┌───────────────────────────┐
│ Angular frontend │ ────────────────────▶ │ Spring Boot backend       │
│ Nginx :4200      │                       │ Modular monolith :3000    │
└──────────────────┘                       └─────────────┬─────────────┘
                                                      │
                          ┌───────────────────────────┼───────────────────┐
                          ▼                           ▼                   ▼
                 PostgreSQL/Supabase          PayPal & VietQR         SendGrid
```

Backend được chia theo feature dưới package `vn.aims`:

- `auth`: đăng nhập, đổi mật khẩu và JWT.
- `user`: tài khoản, vai trò và nhật ký quản trị.
- `product`: catalog, tồn kho và audit sản phẩm.
- `cart`: kiểm tra giỏ hàng và tính phí vận chuyển.
- `order`: đặt hàng, theo dõi và quản lý vòng đời đơn.
- `payment`, `paypal`, `vietqr`: giao dịch và tích hợp cổng thanh toán.
- `notification`: outbox, mẫu email và SendGrid.
- `common`: cấu hình và thành phần dùng chung.

Mỗi feature sử dụng các package Spring quen thuộc như `controller`, `service`, `repository`,
`entity`, `dto` và `exception`. Xem thêm [cấu trúc backend](docs/backend-structure.md).

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Spring MVC, Spring Security, Spring Data JPA |
| Frontend | Angular 21, TypeScript 5.9, RxJS, Tailwind CSS 4 |
| Database | PostgreSQL/Supabase, Flyway |
| Thanh toán | PayPal REST API, VietQR API |
| Email | SendGrid |
| Kiểm thử | JUnit 5, Spring Boot Test, Testcontainers, Vitest |
| Đóng gói | Maven Wrapper, npm, Docker Compose, Nginx |

## Cấu trúc repository

```text
AIMS/
├── src/
│   ├── backend/                 # Spring Boot REST API
│   │   ├── src/main/java/vn/aims
│   │   ├── src/main/resources/db/migration
│   │   └── src/test
│   └── frontend/                # Angular SPA
│       └── src/app
├── docs/                        # API, kiến trúc và hướng dẫn triển khai
├── tools/                       # Công cụ kiểm tra và vận hành
├── docker-compose.yml
└── README.md
```

## Chạy dự án bằng Docker Compose

### Yêu cầu

- Docker Desktop hoặc Docker Engine có Docker Compose.
- Một PostgreSQL/Supabase instance mà máy chạy Docker có thể truy cập.
- File `.env.supabase` ở thư mục gốc dự án.

Tối thiểu, `.env.supabase` cần các biến sau:

```dotenv
AIMS_DB_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
AIMS_DB_USERNAME=<database-user>
AIMS_DB_PASSWORD=<database-password>
JWT_SECRET=<random-secret-at-least-32-bytes>
APP_PUBLIC_URL=https://<frontend-public-host>
NOTIFICATIONS_ENABLED=false
```

Không commit file môi trường hoặc khóa bí mật. Compose sử dụng schema `aims_java`; Flyway tự tạo và
kiểm tra schema khi backend khởi động.

Khởi động toàn bộ ứng dụng:

```sh
docker compose up -d --build --wait
```

Sau khi các container healthy:

- Frontend: <http://localhost:4200>
- Backend API: <http://localhost:3000>
- Health check: <http://localhost:3000/actuator/health>

Theo dõi log hoặc dừng hệ thống:

```sh
docker compose logs -f
docker compose down
```

Database nằm ngoài Compose nên `docker compose down` không xóa dữ liệu.

## Cấu hình tích hợp

Các tích hợp bên ngoài được cấu hình qua biến môi trường:

| Nhóm | Biến chính |
| --- | --- |
| Database | `AIMS_DB_URL`, `AIMS_DB_USERNAME`, `AIMS_DB_PASSWORD`, `AIMS_DB_SCHEMA` |
| JWT | `JWT_SECRET` |
| Ứng dụng | `APP_PUBLIC_URL`, `ALLOWED_ORIGINS`, `PORT` |
| PayPal | `PAYPAL_API_BASE_URL`, `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` |
| VietQR | `VIETQR_API_BASE_URL`, `VIETQR_USERNAME`, `VIETQR_PASSWORD`, thông tin tài khoản ngân hàng |
| VietQR callback | `VIETQR_MERCHANT_USERNAME`, `VIETQR_MERCHANT_PASSWORD` |
| Email | `NOTIFICATIONS_ENABLED`, `SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL` |

PayPal và VietQR mặc định sử dụng môi trường sandbox/development. Email mặc định không gửi cho đến
khi `NOTIFICATIONS_ENABLED=true`. Danh sách biến đầy đủ và lưu ý production nằm trong
[hướng dẫn deployment](docs/deployment.md).

## Chạy ở chế độ phát triển

Backend yêu cầu Java 21, Docker và một PostgreSQL có thể truy cập. Cấu hình các biến database/JWT
trong môi trường chạy hoặc IDE, sau đó:

```sh
cd src/backend
SPRING_PROFILES_ACTIVE=container ./mvnw spring-boot:run
```

Frontend yêu cầu Node.js tương thích Angular 21 và npm:

```sh
cd src/frontend
npm ci
npm start
```

Frontend development chạy tại `http://localhost:4200` và gọi backend tại
`http://localhost:3000`.

## Xác thực và phân quyền

Spring Security xử lý đăng nhập qua `AuthenticationManager` và phát JWT cho tài khoản nội bộ.
Các request cần xác thực gửi token theo chuẩn:

```http
Authorization: Bearer <access-token>
```

| Vai trò | Phạm vi chính |
| --- | --- |
| `ADMIN` | Quản trị người dùng, vai trò, trạng thái và audit log |
| `PRODUCT_MANAGER` | Quản lý sản phẩm, đơn hàng, hoàn tiền và audit log |

Khách mua hàng không cần tài khoản. Quyền truy cập một đơn cụ thể được kiểm tra bằng order access
token do backend cấp khi tạo đơn. Dự án không tạo sẵn tài khoản hoặc mật khẩu mặc định.

## API chính

| Nhóm | Endpoint gốc | Quyền truy cập |
| --- | --- | --- |
| Catalog | `/api/products` | Công khai khi đọc; Product Manager khi ghi |
| Authentication | `/api/auth` | Đăng nhập công khai; đổi mật khẩu cần JWT |
| Cart và đơn hàng | `/api/orders` | Khách hàng hoặc Product Manager tùy thao tác |
| Quản trị người dùng | `/api/users` | Admin |
| PayPal | `/api/paypal/order` | Order token hoặc Product Manager tùy thao tác |
| VietQR | `/api/vietqr/payments` | Order token, merchant callback hoặc Product Manager |
| Health | `/actuator/health` | Công khai |

Chi tiết method, header, payload và response được mô tả tại [API contract](docs/api-contract.md).

## Kiểm thử

Backend integration test sử dụng PostgreSQL thật qua Testcontainers, vì vậy Docker phải đang chạy:

```sh
cd src/backend
./mvnw -B verify
```

Kiểm thử và build frontend:

```sh
cd src/frontend
npm ci
npm test -- --watch=false
npm run build
```

Kiểm tra cấu hình Compose:

```sh
python3 tools/verify-compose.py
docker compose config --quiet
```

CI thực hiện Maven verify, Angular test/build và kiểm tra Docker image trước khi tích hợp.

## Tài liệu

- [Cấu trúc backend](docs/backend-structure.md)
- [API contract](docs/api-contract.md)
- [Cấu hình và deployment](docs/deployment.md)
- [Rủi ro và quyết định kỹ thuật](docs/migration-risks.md)
