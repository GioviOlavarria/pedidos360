import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/api/catalog.service';
import { OrdersService } from '../../core/api/orders.service';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage, customerIdFromAccount } from '../../core/format';
import { Order, OrderStatus, Product } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusBadge } from '../../shared/status-badge';
import { DatePipe } from '@angular/common';

type Segment = 'all' | 'products' | 'tracking';

const TRACKING_STEPS: { key: OrderStatus; label: string; desc: string; icon: string }[] = [
  { key: 'CREADO', label: 'Creado', desc: 'Pedido ingresado en sistema', icon: '📝' },
  { key: 'ACEPTADO', label: 'Aceptado', desc: 'Validación comercial aprobada', icon: '✅' },
  { key: 'EN_PREPARACION', label: 'En Preparación', desc: 'Empaque y etiquetado en bodega', icon: '📦' },
  { key: 'DESPACHADO', label: 'Despachado', desc: 'En ruta con Pedidos360 Express', icon: '🚚' },
  { key: 'ENTREGADO', label: 'Entregado', desc: 'Entregado conforme en destino', icon: '🎉' },
];

/** Inicio público: productos para seleccionar y seguimiento de pedidos por número (sin iniciar sesión). */
@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MoneyPipe, StatusBadge, DatePipe],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly orders = inject(OrdersService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);

  protected readonly steps = TRACKING_STEPS;
  protected readonly demoIds = [1, 2, 3, 4];
  protected readonly tabs: { key: Segment; label: string; icon: string }[] = [
    { key: 'all', label: 'Todos los segmentos', icon: '📑' },
    { key: 'products', label: '1. Productos para Seleccionar', icon: '🛍️' },
    { key: 'tracking', label: '2. Seguimiento de Pedidos', icon: '📦' },
  ];

  protected readonly segment = signal<Segment>('all');

  // Productos
  protected readonly products = signal<Product[]>([]);
  protected readonly loadingProducts = signal(true);
  protected readonly search = signal('');
  protected readonly quantities = signal<Record<number, number>>({});
  protected readonly selected = signal<{ product: Product; quantity: number } | null>(null);
  protected readonly purchasing = signal(false);
  protected readonly purchased = signal<Order | null>(null);

  protected readonly filteredProducts = computed(() => {
    const term = this.search().toLowerCase();
    return this.products().filter(
      (p) => (p.name || '').toLowerCase().includes(term) || (p.description || '').toLowerCase().includes(term),
    );
  });

  // Seguimiento
  protected readonly trackingId = signal('');
  protected readonly trackedOrder = signal<Order | null>(null);
  protected readonly loadingTracking = signal(false);
  protected readonly trackingError = signal('');
  protected readonly currentStep = computed(() => {
    const status = this.trackedOrder()?.status;
    const index = TRACKING_STEPS.findIndex((s) => s.key === status);
    return index >= 0 ? index : 0;
  });

  ngOnInit(): void {
    // Un administrador usa su panel en vez del inicio público.
    if (this.auth.isAdmin()) {
      void this.router.navigateByUrl('/dashboard', { replaceUrl: true });
      return;
    }
    if (this.route.snapshot.data['segment'] === 'tracking') {
      this.segment.set('tracking');
    }
    this.loadProducts();
  }

  private loadProducts(): void {
    this.loadingProducts.set(true);
    this.catalog.getProducts().subscribe({
      next: (products) => {
        this.products.set(products ?? []);
        this.loadingProducts.set(false);
      },
      error: () => {
        this.products.set([]);
        this.loadingProducts.set(false);
      },
    });
  }

  protected goTo(segment: Segment, anchor: string): void {
    this.segment.set(segment);
    setTimeout(() => document.getElementById(anchor)?.scrollIntoView({ behavior: 'smooth' }));
  }

  protected qty(product: Product): number {
    return this.quantities()[product.id] ?? 1;
  }

  protected changeQty(product: Product, delta: number): void {
    this.quantities.update((q) => ({
      ...q,
      [product.id]: Math.max(1, Math.min(product.stock, (q[product.id] ?? 1) + delta)),
    }));
  }

  protected select(product: Product): void {
    this.selected.set({ product, quantity: this.qty(product) });
  }

  protected confirmPurchase(): void {
    const selection = this.selected();
    if (!selection) return;
    if (!this.auth.isAuthenticated()) {
      void this.router.navigateByUrl('/login');
      return;
    }
    this.purchasing.set(true);
    const customerId = customerIdFromAccount(this.auth.account());
    this.orders.purchase(selection.product, selection.quantity, customerId).subscribe({
      next: (order) => {
        this.purchased.set(order);
        this.purchasing.set(false);
        this.selected.set(null);
        this.loadProducts();
      },
      error: (err) => {
        this.purchasing.set(false);
        this.selected.set(null);
        window.alert(apiErrorMessage(err, 'No se pudo registrar la compra. Intente nuevamente.'));
      },
    });
  }

  protected track(idToSearch?: string): void {
    const id = (idToSearch ?? this.trackingId()).trim().replace('#', '');
    if (!id) {
      this.trackingError.set('Por favor ingresa un número de pedido.');
      return;
    }
    this.trackingId.set(id);
    this.loadingTracking.set(true);
    this.trackingError.set('');
    this.trackedOrder.set(null);

    this.orders.getOrder(id).subscribe({
      next: (order) => {
        this.trackedOrder.set(order);
        this.loadingTracking.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.trackingError.set(
          err.status === 404
            ? `No existe un pedido con el número #${id}.`
            : apiErrorMessage(err, 'No se pudo consultar el pedido. Intente nuevamente.'),
        );
        this.loadingTracking.set(false);
      },
    });
  }

  protected trackPurchased(): void {
    const order = this.purchased();
    this.purchased.set(null);
    if (!order) return;
    this.goTo('tracking', 'segmento-seguimiento');
    this.track(String(order.id));
  }
}
