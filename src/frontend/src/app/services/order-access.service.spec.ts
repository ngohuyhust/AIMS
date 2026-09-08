import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { beforeEach, afterEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../app.config';
import { OrderAccessService } from './order-access.service';
import { OrderService } from './order.service';
import { PaymentService } from './payment.service';

describe('guest order capabilities', () => {
  const token = 'a'.repeat(64);
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '' }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('remembers creation token and attaches it to detail and delivery edits only', () => {
    const orders = TestBed.inject(OrderService);
    const delivery = { receiverName: 'Test', email: 'a@example.test', phoneNumber: '0912345678', address: 'Test', province: 'HN' };
    orders.placeOrder([], delivery).subscribe();
    const create = http.expectOne('/api/orders');
    expect(create.request.headers.has('x-order-token')).toBe(false);
    create.flush({ orderID: 7, customerAccessToken: token });
    orders.getOrderDetail(7).subscribe();
    const detail = http.expectOne('/api/orders/7');
    expect(detail.request.headers.get('x-order-token')).toBe(token); detail.flush({});
    orders.updateDeliveryInfo(7, delivery).subscribe();
    const edit = http.expectOne('/api/orders/7/delivery-info');
    expect(edit.request.headers.get('x-order-token')).toBe(token); edit.flush({});
    orders.getOrderDetail(8).subscribe();
    const other = http.expectOne('/api/orders/8');
    expect(other.request.headers.has('x-order-token')).toBe(false); other.flush({});
  });

  it('restores the same-tab capability and shares it with payment detail reads', () => {
    sessionStorage.setItem('aims_order_token_7', token);
    TestBed.inject(PaymentService).getOrderDetail(7).subscribe();
    const detail = http.expectOne('/api/orders/7');
    expect(detail.request.headers.get('x-order-token')).toBe(token); detail.flush({});
  });

  it('remembers a link token only after successful customer detail access', () => {
    TestBed.inject(PaymentService).getCustomerOrderDetail(7, token).subscribe();
    const detail = http.expectOne(`/api/customer/orders/7?token=${token}`);
    expect(TestBed.inject(OrderAccessService).headers(7).has('x-order-token')).toBe(false);
    detail.flush({});
    expect(TestBed.inject(OrderAccessService).headers(7).get('x-order-token')).toBe(token);
  });
  it('sends the matching capability on PayPal create/capture while preserving payloads', () => {
    TestBed.inject(OrderAccessService).remember(7, token);
    const payments = TestBed.inject(PaymentService);
    payments.createOrder(7).subscribe();
    const create = http.expectOne('/api/paypal/order/create');
    expect(create.request.headers.get('x-order-token')).toBe(token);
    expect(create.request.body).toEqual({ orderID: 7 }); create.flush({});
    payments.captureOrder('PAYPAL-7', 7).subscribe();
    const capture = http.expectOne('/api/paypal/order/capture');
    expect(capture.request.headers.get('x-order-token')).toBe(token);
    expect(capture.request.body).toEqual({ paypalOrderID: 'PAYPAL-7', orderID: 7 }); capture.flush({});
    payments.refundOrder(7).subscribe();
    const refund = http.expectOne('/api/paypal/order/refund');
    expect(refund.request.headers.has('x-order-token')).toBe(false);
    expect(refund.request.body).toEqual({ orderID: 7 }); refund.flush({});
  });

});
