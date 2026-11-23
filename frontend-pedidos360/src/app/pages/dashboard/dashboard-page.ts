import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrdersService } from '../../core/api/orders.service';
import { AuthService } from '../../core/auth/auth.service';
import { formatMoney } from '../../core/format';
import { countByStatus, percent } from '../../core/metrics';
import { Order } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusBadge } from '../../shared/status-badge';

/** Panel principal del Admin/Operator: indicadores del mes, pedidos recientes y distribución. */
@Component({
  selector: 'app-dashboard-page',
  imports: [RouterLink, DatePipe, MoneyPipe, StatusBadge],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
})
export class DashboardPage implements OnInit {
  private readonly ordersApi = inject(OrdersService);
  protected readonly auth = inject(AuthService);

  protected readonly today = new Date();
  protected readonly orders = signal<Order[]>([]);
  protected readonly loading = signal(true);

  protected readonly firstName = computed(() => this.auth.account()?.name?.split(' ')[0] ?? 'usuario');

  protected readonly kpis = computed(() => {
    const orders = this.orders();
    const now = this.today;
    const thisMonth = orders.filter((o) => {
      const d = new Date(o.createdAt);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });
    // Pagados que el Admin aún debe aceptar o preparar.
    const pending = orders.filter((o) => o.status === 'CREADO' || o.status === 'ACEPTADO').length;
    return [
      { icon: '📦', value: String(thisMonth.length), label: 'Pedidos este mes' },
      { icon: '✅', value: `${percent(countByStatus(orders, 'ENTREGADO'), orders.length)}%`, label: 'Tasa de entrega' },
      { icon: '🕒', value: String(pending), label: 'Pedidos por gestionar' },
      {
        icon: '💰',
        value: formatMoney(
          thisMonth
            .filter((o) => o.status !== 'PENDIENTE_PAGO' && o.status !== 'CANCELADO')
            .reduce((sum, o) => sum + (Number(o.total) || 0), 0),
        ),
        label: 'Ingresos del mes (pagados)',
      },
    ];
  });

  protected readonly recent = computed(() =>
    [...this.orders()].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()).slice(0, 5),
  );

  protected readonly distribution = computed(() => {
    const orders = this.orders();
    return [
      { label: 'Entregados', pct: percent(countByStatus(orders, 'ENTREGADO'), orders.length), color: '#15803d' },
      { label: 'En tránsito', pct: percent(countByStatus(orders, 'DESPACHADO'), orders.length), color: '#ff6b00' },
      { label: 'En preparación', pct: percent(countByStatus(orders, 'EN_PREPARACION'), orders.length), color: '#854d0e' },
      { label: 'Cancelados', pct: percent(countByStatus(orders, 'CANCELADO'), orders.length), color: '#b91c1c' },
    ];
  });

  ngOnInit(): void {
    this.ordersApi.getOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        console.error(err);
        this.loading.set(false);
      },
    });
  }
}
