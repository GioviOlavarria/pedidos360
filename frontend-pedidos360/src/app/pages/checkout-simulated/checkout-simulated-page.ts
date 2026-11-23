import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PaymentsService } from '../../core/api/payments.service';
import { apiErrorMessage } from '../../core/format';
import { Payment } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/**
 * Pasarela de prueba (PAYMENT_PROVIDER=simulado): reemplaza la página de Flow mientras no haya cuenta.
 * El resultado sigue el mismo camino que un pago real (payments → Kafka → orders → boleta).
 */
@Component({
  selector: 'app-checkout-simulated-page',
  imports: [MoneyPipe],
  template: `
    <div class="p-page wrap">
      <div class="gateway">
        <div class="head">
          <span class="badge">MODO PRUEBA</span>
          <h1>Pasarela de pago simulada</h1>
          <p>Imita el pago en Flow. Con PAYMENT_PROVIDER=flow aquí aparece la página real de Flow.</p>
        </div>

        @if (error()) {
          <div class="p-alert-error">{{ error() }}</div>
        } @else if (payment(); as p) {
          <div class="p-summary">
            <div class="p-summary-row"><span>Comercio</span><strong>Pedidos360</strong></div>
            <div class="p-summary-row"><span>Pedido</span><strong>#{{ p.orderId }}</strong></div>
            <div class="p-summary-row"><span>Orden de pago</span><span>{{ p.providerReference }}</span></div>
            <div class="p-summary-total"><span>Total a pagar</span><strong>{{ p.amount | money }}</strong></div>
          </div>
          <div class="card">💳 Tarjeta de prueba •••• 4242</div>
          <div class="actions">
            <button type="button" class="p-btn p-btn-secondary" (click)="resolve(false)" [disabled]="busy()">
              Rechazar pago
            </button>
            <button type="button" class="p-btn p-btn-primary" (click)="resolve(true)" [disabled]="busy()">
              {{ busy() ? 'Procesando...' : 'Pagar ' + (p.amount | money) }}
            </button>
          </div>
        } @else {
          <p class="p-muted">Cargando orden de pago...</p>
        }
      </div>
    </div>
  `,
  styles: `
    .wrap {
      display: flex;
      justify-content: center;
    }
    .gateway {
      background: #fff;
      border-radius: 16px;
      padding: 32px;
      width: 460px;
      max-width: 100%;
      box-shadow: 0 10px 30px rgba(0, 48, 135, 0.1);
      border-top: 4px solid #22c55e;
    }
    .head {
      text-align: center;
      margin-bottom: 24px;
    }
    .badge {
      background: #fef3c7;
      color: #92400e;
      font-size: 0.7rem;
      font-weight: 800;
      padding: 4px 10px;
      border-radius: 12px;
      letter-spacing: 0.05em;
    }
    h1 {
      font-size: 1.4rem;
      color: var(--navy);
      margin: 12px 0 6px;
    }
    .head p {
      color: var(--slate-500);
      font-size: 0.82rem;
      margin: 0;
    }
    .card {
      border: 1px dashed var(--slate-300);
      border-radius: 10px;
      padding: 12px;
      text-align: center;
      color: var(--slate-600);
      margin-bottom: 20px;
    }
    .actions {
      display: flex;
      gap: 12px;
    }
    .actions .p-btn {
      flex: 1;
    }
  `,
})
export class CheckoutSimulatedPage implements OnInit {
  private readonly payments = inject(PaymentsService);
  private readonly router = inject(Router);

  /** ?token=... (withComponentInputBinding). */
  readonly token = input('');

  protected readonly payment = signal<Payment | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');

  ngOnInit(): void {
    this.payments.byToken(this.token()).subscribe({
      next: (payment) => this.payment.set(payment),
      error: (err) => this.error.set(apiErrorMessage(err, 'Orden de pago no encontrada.')),
    });
  }

  protected resolve(approved: boolean): void {
    this.busy.set(true);
    this.payments.resolveSimulated(this.token(), approved).subscribe({
      next: (payment) =>
        void this.router.navigate(['/checkout/resultado'], { queryParams: { orderId: payment.orderId }, replaceUrl: true }),
      error: (err) => {
        this.busy.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo procesar el pago.'));
      },
    });
  }
}
