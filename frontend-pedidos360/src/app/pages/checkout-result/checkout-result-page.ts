import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';
import { OrdersService } from '../../core/api/orders.service';
import { PaymentsService } from '../../core/api/payments.service';
import { CheckoutService } from '../../core/checkout.service';
import { apiErrorMessage } from '../../core/format';
import { Order } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/**
 * Flow devuelve aquí al comprador (/checkout/resultado?orderId=N). El resultado llega a orders por Kafka, así
 * que se consulta el pedido cada 2 segundos hasta que deje de estar PENDIENTE_PAGO (y luego hasta ver la boleta).
 */
@Component({
  selector: 'app-checkout-result-page',
  imports: [RouterLink, MoneyPipe],
  templateUrl: './checkout-result-page.html',
  styleUrl: './checkout-result-page.css',
})
export class CheckoutResultPage implements OnInit {
  private readonly orders = inject(OrdersService);
  private readonly payments = inject(PaymentsService);
  private readonly checkout = inject(CheckoutService);
  private readonly destroyRef = inject(DestroyRef);

  readonly orderId = input<string>('');

  protected readonly order = signal<Order | null>(null);
  protected readonly error = signal('');
  protected readonly polling = signal(true);
  protected readonly retrying = signal(false);

  protected readonly state = computed<'pending' | 'paid' | 'failed'>(() => {
    const status = this.order()?.status;
    if (!status || status === 'PENDIENTE_PAGO') return 'pending';
    return status === 'CANCELADO' && !this.order()?.paidAt ? 'failed' : 'paid';
  });

  private poll?: Subscription;

  ngOnInit(): void {
    // Por si la confirmación de Flow no llegó (p. ej. en localhost), se pide a payments volver a consultar.
    this.payments.refresh(Number(this.orderId())).subscribe({ error: () => undefined });
    this.startPolling();
  }

  protected startPolling(): void {
    this.poll?.unsubscribe();
    this.polling.set(true);
    let attempts = 0;
    this.poll = timer(0, 2000)
      .pipe(
        switchMap(() => this.orders.getOrder(this.orderId())),
        // Sigue mientras no haya resultado o, si se pagó, hasta que llegue la boleta (máx. ~40 s).
        takeWhile((order) => ++attempts < 20 && (order.status === 'PENDIENTE_PAGO' || !order.receiptFolio), true),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (order) => {
          this.order.set(order);
          if (order.status === 'CANCELADO') this.polling.set(false);
        },
        error: (err) => {
          this.error.set(apiErrorMessage(err, 'No se pudo consultar el pedido.'));
          this.polling.set(false);
        },
        complete: () => this.polling.set(false),
      });
  }

  protected checkAgain(): void {
    this.payments.refresh(Number(this.orderId())).subscribe({
      next: () => this.startPolling(),
      error: () => this.startPolling(),
    });
  }

  protected retryPayment(): void {
    this.retrying.set(true);
    this.checkout.payOrderUrl(Number(this.orderId())).subscribe({
      next: (url) => this.checkout.goToGateway(url),
      error: (err) => {
        this.retrying.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo reintentar el pago.'));
      },
    });
  }
}
