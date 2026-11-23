import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { CatalogService } from '../../core/api/catalog.service';
import { OrdersService } from '../../core/api/orders.service';
import {
  ReportFilter,
  filterOrders,
  kpis,
  monthlyRevenue,
  statusDistribution,
  topProducts,
} from '../../core/metrics';
import { Order, Product, ProductSales } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/** Reportes del Admin: ingresos, distribución por estado, más vendidos y ventas en streaming (Kafka). */
@Component({
  selector: 'app-reports-page',
  imports: [MoneyPipe],
  templateUrl: './reports-page.html',
  styleUrl: './reports-page.css',
})
export class ReportsPage implements OnInit {
  private readonly ordersApi = inject(OrdersService);
  private readonly catalog = inject(CatalogService);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly orders = signal<Order[]>([]);
  protected readonly products = signal<Product[]>([]);
  /** Ventas que catalog calcula consumiendo el tópico Kafka pedidos360.orders.events. */
  protected readonly streamSales = signal<ProductSales[] | null>(null);
  protected readonly filter = signal<ReportFilter>('ACTIVE');

  protected readonly filtered = computed(() => filterOrders(this.orders(), this.filter()));
  protected readonly stats = computed(() => kpis(this.filtered()));
  protected readonly months = computed(() => monthlyRevenue(this.filtered()));
  protected readonly top = computed(() => topProducts(this.filtered(), this.products()));
  protected readonly distribution = computed(() => statusDistribution(this.orders()));

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({ orders: this.ordersApi.getOrders(), products: this.catalog.getProducts() }).subscribe({
      next: ({ orders, products }) => {
        this.orders.set(orders ?? []);
        this.products.set(products ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(
          'No se pudieron obtener las métricas del servidor. Asegúrese de que el backend esté en ejecución.',
        );
        this.loading.set(false);
      },
    });
    // Si la estadística de Kafka falla, el resto del reporte sigue funcionando.
    this.catalog.getSalesStats().subscribe({
      next: (sales) => this.streamSales.set(sales ?? []),
      error: () => this.streamSales.set(null),
    });
  }

  protected setFilter(value: string): void {
    this.filter.set(value as ReportFilter);
  }
}
