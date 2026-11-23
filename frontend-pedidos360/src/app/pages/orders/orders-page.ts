import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrdersService } from '../../core/api/orders.service';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage } from '../../core/format';
import { Order } from '../../core/models';
import { OrderDetail } from '../../components/order-detail/order-detail';
import { OrderForm } from '../../components/order-form/order-form';
import { OrderList } from '../../components/order-list/order-list';

/**
 * Pedidos: el Admin gestiona todos (estado, edición, eliminación); el cliente ve los suyos.
 * El backend filtra por el correo del JWT, así que un cliente nunca recibe pedidos ajenos.
 */
@Component({
  selector: 'app-orders-page',
  imports: [RouterLink, OrderList, OrderDetail, OrderForm],
  templateUrl: './orders-page.html',
  styleUrl: './orders-page.css',
})
export class OrdersPage implements OnInit {
  private readonly ordersApi = inject(OrdersService);
  protected readonly auth = inject(AuthService);

  protected readonly orders = signal<Order[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly selectedId = signal<number | null>(null);
  protected readonly refreshKey = signal(0);

  protected readonly showForm = signal(false);
  protected readonly editing = signal<Order | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.ordersApi.getOrders().subscribe({
      next: (orders) => {
        const sorted = [...(orders ?? [])].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
        );
        this.orders.set(sorted);
        if (!sorted.some((o) => o.id === this.selectedId())) {
          this.selectedId.set(sorted[0]?.id ?? null);
        }
        this.refreshKey.update((k) => k + 1);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Error al cargar pedidos del servidor.');
        this.loading.set(false);
      },
    });
  }

  protected openForm(order: Order | null): void {
    this.editing.set(order);
    this.showForm.set(true);
  }

  protected formClosed(saved: boolean): void {
    this.showForm.set(false);
    this.editing.set(null);
    if (saved) this.load();
  }

  protected deleteOrder(order: Order): void {
    if (!window.confirm(`¿Seguro que deseas eliminar el pedido #${order.id}?`)) return;
    this.ordersApi.deleteOrder(order.id).subscribe({
      next: () => {
        this.orders.update((list) => list.filter((o) => o.id !== order.id));
        if (this.selectedId() === order.id) this.selectedId.set(this.orders()[0]?.id ?? null);
      },
      error: (err) => window.alert(apiErrorMessage(err, 'Error al eliminar el pedido.')),
    });
  }
}
