import { Injectable, Inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { OrderAccessService } from './order-access.service';
import { API_BASE_URL } from '../app.config';

@Injectable({
  providedIn: 'root'
})
export class PaymentService {
  private readonly vietqrOrders = new Map<number, number>();
  constructor(
    private readonly http: HttpClient,
    private readonly orderAccess: OrderAccessService,
    @Inject(API_BASE_URL) private readonly baseUrl: string
  ) { }

  createOrder(orderId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/paypal/order/create`, {
      orderID: orderId
    }, { headers: this.orderAccess.headers(orderId) });
  }

  captureOrder(paypalOrderID: string, orderId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/paypal/order/capture`, {
      paypalOrderID,
      orderID: orderId
    }, { headers: this.orderAccess.headers(orderId) });
  }
  getOrderDetail(orderId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/api/orders/${orderId}`, { headers: this.orderAccess.headers(orderId) });
  }

  getCustomerOrderDetail(orderId: number, token: string): Observable<any> {
    return this.http.get(`${this.baseUrl}/api/customer/orders/${orderId}?token=${encodeURIComponent(token)}`).pipe(
      tap(() => this.orderAccess.remember(orderId, token))
    );
  }

  refundOrder(orderId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/paypal/order/refund`, {
      orderID: orderId
    });
  }

  cancelOrder(orderId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/orders/${orderId}/cancel`, {});
  }

  cancelCustomerOrder(orderId: number, token: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/customer/orders/${orderId}/cancel?token=${encodeURIComponent(token)}`, {});
  }

  createVietqrPayment(orderId: number, amount: number, content: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/vietqr/payments`, {
      orderId,
      amount,
      content
    }, { headers: this.orderAccess.headers(orderId) }).pipe(
      tap((response: any) => {
        if (Number.isInteger(response.paymentId) && response.orderId === orderId) {
          this.vietqrOrders.set(response.paymentId, orderId);
        }
      })
    );
  }

  getVietqrPaymentStatus(paymentId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/api/vietqr/payments/${paymentId}/status`, {
      headers: this.orderAccess.headers(this.vietqrOrders.get(paymentId) ?? 0)
    });
  }

  triggerVietqrTestCallback(paymentId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/vietqr/payments/${paymentId}/trigger-callback`, {});
  }
}
