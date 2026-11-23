import { Component, input, output } from '@angular/core';
import { Order } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusBadge } from '../../shared/status-badge';

/** Tabla de pedidos. El Admin ve la columna de cliente y puede eliminar. */
@Component({
  selector: 'app-order-list',
  imports: [MoneyPipe, StatusBadge],
  template: `
    @if (orders().length === 0) {
      <p class="empty">
        {{ isAdmin() ? 'No hay pedidos registrados en el sistema.'
          : 'No has realizado pedidos todavía. ¡Explora el catálogo y haz tu primera compra!' }}
      </p>
    } @else {
      <div class="scroll">
        <table class="p-table">
          <thead>
            <tr>
              <th>ID</th>
              @if (isAdmin()) {
                <th>Cliente</th>
              }
              <th>Total</th>
              <th>Estado</th>
              <th class="center">Acción</th>
            </tr>
          </thead>
          <tbody>
            @for (order of orders(); track order.id) {
              <tr [class.selected]="order.id === selectedId()">
                <td class="id">#{{ order.id }}</td>
                @if (isAdmin()) {
                  <td class="strong">{{ order.customerName || '#' + order.customerId }}</td>
                }
                <td class="strong">{{ order.total | money }}</td>
                <td><app-status-badge [status]="order.status" [pill]="true" /></td>
                <td class="actions">
                  <button type="button" class="view" (click)="select.emit(order)">Ver Detalle</button>
                  @if (isAdmin()) {
                    <button type="button" class="delete" (click)="remove.emit(order)">Eliminar</button>
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .empty {
      text-align: center;
      padding: 36px 16px;
      color: var(--slate-400);
      font-weight: 500;
      margin: 0;
    }
    .scroll {
      overflow-x: auto;
    }
    .center {
      text-align: center;
    }
    .id {
      font-weight: 700;
      color: var(--slate-500);
    }
    .strong {
      font-weight: 700;
      color: var(--navy);
    }
    tr.selected td {
      background: rgba(255, 107, 0, 0.05);
    }
    .actions {
      display: flex;
      gap: 8px;
      justify-content: center;
    }
    .actions button {
      border: none;
      border-radius: 6px;
      padding: 6px 12px;
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
      transition: all 0.15s;
    }
    .view {
      background: var(--slate-100);
      color: var(--navy);
    }
    .view:hover {
      background: var(--navy);
      color: #fff;
    }
    .delete {
      background: var(--danger-bg);
      color: var(--danger);
    }
    .delete:hover {
      background: var(--danger);
      color: #fff;
    }
  `,
})
export class OrderList {
  readonly orders = input.required<Order[]>();
  readonly isAdmin = input(false);
  readonly selectedId = input<number | null>(null);

  readonly select = output<Order>();
  readonly remove = output<Order>();
}
