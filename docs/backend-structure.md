# Cấu trúc backend

Backend là modular monolith theo feature dưới `vn.aims`. Bên trong mỗi feature, mã nguồn dùng tên
package trực tiếp theo loại lớp quen thuộc trong Spring:

| Package | Trách nhiệm | Ví dụ trong `order` |
| --- | --- | --- |
| `controller` | HTTP endpoint và ánh xạ lỗi HTTP | `OrderController`, `OrderErrorHandler` |
| `service` | Nghiệp vụ, use case và transaction | `OrderService`, `OrderLifecycleService` |
| `repository` | JPA/JDBC và lưu trữ | `OrderRepository`, `OrderLifecycleStore` |
| `entity` | JPA entity và trạng thái lưu trong cơ sở dữ liệu | `Order`, `OrderItem`, `DeliveryInfo`, `Invoice` |
| `dto` | Request, response và dữ liệu truyền qua biên API | `OrderInput`, `OrderResponse` |
| `exception`, `event` | Lỗi nghiệp vụ và application event | `OrderError`, `OrderLifecycleEvent` |

Các tích hợp kỹ thuật có package mang đúng tên chức năng: `auth/security`, `paypal/client`,
`paypal/gateway`, `vietqr/client`, `vietqr/gateway`, `notification/provider` và
`payment/gateway`. Cấu hình Spring dùng chung nằm ở `common/config`.

Quy ước áp dụng cho `auth`, `cart`, `notification`, `order`, `payment`, `paypal`, `product`, `user`
và `vietqr`. Mỗi feature chỉ tạo những package có lớp tương ứng; ví dụ `notification` không có HTTP
controller hoặc JPA entity riêng. Spring quét toàn bộ cây package từ `vn.aims.AimsApplication`.

Việc tách chỉ đổi package/import và phạm vi truy cập cần thiết; URL, JSON, transaction, schema và
hành vi được giữ nguyên. Maven `verify` toàn backend phải qua sau mọi thay đổi cấu trúc tiếp theo.
