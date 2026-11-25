import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Order, OrderRequest, OrderStatus, Product } from '../models';

/** Pedidos (ms-pedidos360-orders a través del BFF). */
@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.bffUrl}/api/orders`;

  /** Admin/Operator reciben todos los pedidos; un cliente solo los suyos (lo filtra el backend). */
  getOrders(): Observable<Order[]> {
    return this.http.get<Order[]>(this.base);
  }

  /** Seguimiento por número de pedido (requiere sesión): Admin/Operator ven cualquiera, el cliente solo los suyos. */
  getOrder(id: number | string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/${id}`);
  }

  /** El backend recalcula precios y total, y descuenta stock. */
  createOrder(data: OrderRequest): Observable<Order> {
    return this.http.post<Order>(this.base, data);
  }

  /** Compra de un producto desde el catálogo. El backend valida precio y stock y envía la boleta. */
  purchase(product: Product, quantity: number, customerId: number): Observable<Order> {
    return this.createOrder({
      customerId,
      total: product.price * quantity,
      items: [{ productId: product.id, quantity, unitPrice: product.price }],
    });
  }

  updateOrder(id: number, data: OrderRequest): Observable<Order> {
    return this.http.put<Order>(`${this.base}/${id}`, data);
  }

  /** CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO, o CANCELADO. */
  updateStatus(id: number, status: OrderStatus): Observable<Order> {
    return this.http.patch<Order>(`${this.base}/${id}/status`, null, { params: new HttpParams().set('status', status) });
  }

  deleteOrder(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
