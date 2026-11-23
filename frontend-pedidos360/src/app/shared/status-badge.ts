import { Component, computed, input } from '@angular/core';
import { STATUS_STYLES, statusLabel } from '../core/order-status';
import { OrderStatus } from '../core/models';

/** Etiqueta de color según el estado del pedido. */
@Component({
  selector: 'app-status-badge',
  template: `<span class="status-badge" [class.pill]="pill()" [style.background]="colors().background"
    [style.color]="colors().color">{{ label() }}</span>`,
  styles: `
    .status-badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.78rem;
      font-weight: 600;
      letter-spacing: 0.02em;
      white-space: nowrap;
    }
    .status-badge.pill {
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.72rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
  `,
})
export class StatusBadge {
  readonly status = input.required<OrderStatus | string>();
  /** Estilo "píldora" en mayúsculas (tablas). */
  readonly pill = input(false);

  protected readonly colors = computed(
    () => STATUS_STYLES[this.status() as OrderStatus] ?? { background: '#f1f5f9', color: '#475569' },
  );
  protected readonly label = computed(() => statusLabel(this.status()));
}
