import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { OrdersService } from '../../core/api/orders.service';
import { apiErrorMessage } from '../../core/format';
import { NEXT_STATUS, STATUS_STEPS, statusLabel } from '../../core/order-status';
import { Order, OrderStatus } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/** Detalle y seguimiento de un pedido, con su boleta SII y las acciones de estado del Admin. */
@Component({
  selector: 'app-order-detail',
  imports: [MoneyPipe, DatePipe],
  templateUrl: './order-detail.html',
  styleUrl: './order-detail.css',
})
export class OrderDetail {
  private readonly orders = inject(OrdersService);

  readonly orderId = input.required<number>();
  readonly isAdmin = input(false);
  /** Cambia cuando la página recarga los pedidos (p. ej. tras editar): fuerza recargar el detalle. */
  readonly refreshKey = input(0);
  readonly changed = output<void>();
  readonly edit = output<Order>();

  protected readonly order = signal<Order | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly steps = STATUS_STEPS;
  protected readonly label = statusLabel;
  protected readonly cancelled = computed(() => this.order()?.status === 'CANCELADO');
  protected readonly currentStep = computed(() => STATUS_STEPS.indexOf(this.order()?.status as OrderStatus));
  protected readonly nextStatus = computed(() => {
    const status = this.order()?.status;
    return status ? NEXT_STATUS[status] : null;
  });

  constructor() {
    // Recarga cada vez que se selecciona otro pedido o la página pide refrescar.
    effect(() => {
      this.refreshKey();
      this.load(this.orderId());
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.error.set('');
    this.orders.getOrder(id).subscribe({
      next: (order) => {
        this.order.set(order);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Error al cargar detalle del pedido');
        this.loading.set(false);
      },
    });
  }

  protected changeStatus(status: OrderStatus): void {
    const order = this.order();
    if (!order) return;
    if (status === 'CANCELADO' && !window.confirm(`¿Cancelar el pedido #${order.id}? Se devolverá el stock.`)) return;
    this.orders.updateStatus(order.id, status).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.changed.emit();
      },
      error: (err) =>
        window.alert(
          apiErrorMessage(err, 'Error al cambiar el estado del pedido. Verifique la regla de transición de estados.'),
        ),
    });
  }
}
