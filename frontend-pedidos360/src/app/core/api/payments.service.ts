import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Payment } from '../models';

/** Pagos (ms-pedidos360-payments a través del BFF): Flow o la pasarela simulada. */
@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.bffUrl}/api/payments`;

  /** Crea la orden de pago del pedido; la respuesta trae redirectUrl (pasarela) a la que hay que navegar. */
  startCheckout(orderId: number): Observable<Payment> {
    return this.http.post<Payment>(this.base, { orderId });
  }

  forOrder(orderId: number): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.base}/orders/${orderId}`);
  }

  /** Vuelve a consultar en la pasarela los pagos pendientes (por si no llegó la confirmación de Flow). */
  refresh(orderId: number): Observable<Payment[]> {
    return this.http.post<Payment[]>(`${this.base}/orders/${orderId}/refresh`, null);
  }

  byToken(token: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.base}/token/${encodeURIComponent(token)}`);
  }

  /** Pasarela simulada: el comprador aprueba o rechaza el pago. */
  resolveSimulated(token: string, approved: boolean): Observable<Payment> {
    return this.http.post<Payment>(`${this.base}/simulado/${encodeURIComponent(token)}/resolve`, null, {
      params: new HttpParams().set('approved', approved),
    });
  }
}
