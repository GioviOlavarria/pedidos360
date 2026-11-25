import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/api/catalog.service';
import { OrdersService } from '../../core/api/orders.service';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart.service';
import { apiErrorMessage } from '../../core/format';
import { Order, OrderStatus, Product } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';
import { StatusBadge } from '../../shared/status-badge';
import { DatePipe } from '@angular/common';

const TRACKING_STEPS: { key: OrderStatus; label: string; desc: string; icon: string }[] = [
  { key: 'CREADO', label: 'Creado', desc: 'Pedido ingresado en sistema', icon: '📝' },
  { key: 'ACEPTADO', label: 'Aceptado', desc: 'Validación comercial aprobada', icon: '✅' },
  { key: 'EN_PREPARACION', label: 'En Preparación', desc: 'Empaque y etiquetado en bodega', icon: '📦' },
  { key: 'DESPACHADO', label: 'Despachado', desc: 'En ruta con Pedidos360 Express', icon: '🚚' },
  { key: 'ENTREGADO', label: 'Entregado', desc: 'Entregado conforme en destino', icon: '🎉' },
];

/**
 * Inicio público (/): productos para seleccionar y, con sesión, el seguimiento de los pedidos propios.
 * /tracking (requiere sesión): solo el seguimiento; el Admin puede consultar cualquier pedido.
 */
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
  protected readonly cart = inject(CartService);
  private readonly route = inject(ActivatedRoute);
  protected readonly auth = inject(AuthService);

  protected readonly steps = TRACKING_STEPS;

  /** En /tracking se muestra solo el seguimiento (sin el catálogo). */
  protected readonly trackingOnly = this.route.snapshot.data['segment'] === 'tracking';

  // Productos
  protected readonly products = signal<Product[]>([]);
  protected readonly loadingProducts = signal(true);
  protected readonly search = signal('');
  protected readonly quantities = signal<Record<number, number>>({});
  /** Aviso breve al agregar al carrito. */
  protected readonly added = signal('');
  private addedTimer?: ReturnType<typeof setTimeout>;

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
  /** Accesos rápidos: los pedidos recientes que el usuario puede ver (los suyos; el Admin, todos). */
  protected readonly recentIds = signal<number[]>([]);
  protected readonly currentStep = computed(() => {
    const status = this.trackedOrder()?.status;
    const index = TRACKING_STEPS.findIndex((s) => s.key === status);
    return index >= 0 ? index : 0;
  });

  ngOnInit(): void {
    // Un administrador usa su panel en vez del inicio público (pero sí puede usar /tracking).
    if (this.auth.isAdmin() && !this.trackingOnly) {
      void this.router.navigateByUrl('/dashboard', { replaceUrl: true });
      return;
    }
    if (!this.trackingOnly) {
      this.loadProducts();
    }
    if (this.auth.isAuthenticated()) {
      this.loadRecentOrders();
    }
  }

  private loadRecentOrders(): void {
    this.orders.getOrders().subscribe({
      next: (orders) => this.recentIds.set((orders ?? []).slice(0, 6).map((o) => o.id)),
      error: () => this.recentIds.set([]),
    });
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

  protected goTo(anchor: string): void {
    document.getElementById(anchor)?.scrollIntoView({ behavior: 'smooth' });
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

  protected addToCart(product: Product): void {
    const quantity = this.qty(product);
    this.cart.add(product, quantity);
    this.added.set(`${quantity} × ${product.name} agregado al carrito`);
    clearTimeout(this.addedTimer);
    this.addedTimer = setTimeout(() => this.added.set(''), 3000);
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
            ? this.auth.isAdmin()
              ? `No existe un pedido con el número #${id}.`
              : `No encontramos el pedido #${id} entre tus pedidos.`
            : apiErrorMessage(err, 'No se pudo consultar el pedido. Intente nuevamente.'),
        );
        this.loadingTracking.set(false);
      },
    });
  }
}
