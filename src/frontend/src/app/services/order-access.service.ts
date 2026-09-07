import { Injectable } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';

/** Guest order capabilities survive reloads/payment redirects in this tab; never sent globally. */
@Injectable({ providedIn: 'root' })
export class OrderAccessService {
  private readonly tokens = new Map<number, string>();

  remember(orderId: number, token?: string): void {
    if (!token || !/^[0-9a-f]{64}$/.test(token)) return;
    this.tokens.set(orderId, token);
    try { sessionStorage.setItem(`aims_order_token_${orderId}`, token); } catch { /* Memory fallback. */ }
  }

  headers(orderId: number): HttpHeaders {
    let token = this.tokens.get(orderId);
    if (!token) {
      try { token = sessionStorage.getItem(`aims_order_token_${orderId}`) ?? undefined; } catch { /* Storage unavailable. */ }
    }
    return token && /^[0-9a-f]{64}$/.test(token)
      ? new HttpHeaders({ 'x-order-token': token }) : new HttpHeaders();
  }
}
