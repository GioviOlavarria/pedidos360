import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap, tap } from 'rxjs';
import { OrdersService } from './api/orders.service';
import { PaymentsService } from './api/payments.service';
import { AuthService } from './auth/auth.service';
import { CartService } from './cart.service';
import { customerIdFromAccount } from './format';

/**
 * Proceso de compra:
 *  1. Se crea el pedido con los productos del carrito (queda PENDIENTE_PAGO y el stock reservado).
 *  2. ms-pedidos360-payments crea la orden de pago en Flow (o en la pasarela simulada).
 *  3. El navegador se va a la pasarela; al terminar, Flow lo devuelve a /checkout/resultado.
 */
@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly orders = inject(OrdersService);
  private readonly payments = inject(PaymentsService);
  private readonly cart = inject(CartService);
  private readonly auth = inject(AuthService);

  /** Crea el pedido con el carrito y devuelve la URL de la pasarela. */
  checkoutCart(): Observable<string> {
    const items = this.cart.items();
    return this.orders
      .createOrder({
        customerId: customerIdFromAccount(this.auth.account()),
        total: this.cart.total(),
        items: items.map((i) => ({ productId: i.productId, quantity: i.quantity, unitPrice: i.price })),
      })
      .pipe(
        // El pedido ya reservó el stock: el carrito se vacía aunque el pago se haga después.
        tap(() => this.cart.clear()),
        switchMap((order) => this.payOrderUrl(order.id)),
      );
  }

  /** Inicia (o reintenta) el pago de un pedido pendiente y devuelve la URL de la pasarela. */
  payOrderUrl(orderId: number): Observable<string> {
    return this.payments.startCheckout(orderId).pipe(map((payment) => payment.redirectUrl ?? ''));
  }

  /** Lleva el navegador a la pasarela (Flow es un sitio externo, por eso no se usa el router). */
  goToGateway(url: string): void {
    window.location.assign(url);
  }
}
