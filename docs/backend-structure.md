# Cấu trúc backend

Backend vẫn là modular monolith theo feature dưới `vn.aims`. Với feature đủ lớn, mã nguồn được tách
tiếp theo bốn vai trò:

| Package | Trách nhiệm | Ví dụ trong `order` |
| --- | --- | --- |
| `api` | HTTP controller, parse/validate request, ánh xạ lỗi HTTP | `OrderController`, `OrderInput`, `OrderErrorHandler` |
| `application` | Use case, transaction và response model | `OrderService`, `OrderLifecycleService`, `OrderResponse` |
| `domain` | Entity và trạng thái nghiệp vụ cốt lõi | `Order`, `OrderItem`, `DeliveryInfo`, `Invoice` |
| `infrastructure` | JPA/JDBC và lưu trữ kỹ thuật | `OrderRepository`, `OrderLifecycleStore` |

Luồng phụ thuộc ưu tiên là `api -> application -> domain`; `application` gọi `infrastructure` qua
các lớp lưu trữ hiện có. Code bên ngoài feature chỉ import kiểu cần chia sẻ từ package cụ thể, ví dụ
payment liên kết với `vn.aims.order.domain.Order`. Spring vẫn quét toàn bộ cây package từ
`vn.aims.AimsApplication`, nên việc tách thư mục không đổi endpoint hay cách khởi động.

Áp dụng theo từng feature trong checkpoint riêng. Mỗi lần tách chỉ thay package/import và phạm vi
truy cập cần thiết; giữ nguyên URL, JSON, transaction, schema và hành vi. Sau mỗi feature phải chạy
test riêng và Maven `verify` toàn backend trước khi chuyển sang feature tiếp theo.
