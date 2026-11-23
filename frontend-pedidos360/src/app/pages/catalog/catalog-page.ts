import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CatalogService } from '../../core/api/catalog.service';
import { OrdersService } from '../../core/api/orders.service';
import { AuthService } from '../../core/auth/auth.service';
import { apiErrorMessage, customerIdFromAccount } from '../../core/format';
import { Order, Product } from '../../core/models';
import { ProductCard } from '../../components/product-card/product-card';
import { ProductForm } from '../../components/product-form/product-form';
import { MoneyPipe } from '../../shared/money.pipe';

/** Catálogo: público para ver, compra para clientes y administración de inventario para el Admin. */
@Component({
  selector: 'app-catalog-page',
  imports: [RouterLink, ProductCard, ProductForm, MoneyPipe],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.css',
})
export class CatalogPage implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly orders = inject(OrdersService);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly customerId = computed(() => customerIdFromAccount(this.auth.account()));

  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  // Administración
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Product | null>(null);

  // Compra del cliente
  protected readonly buying = signal<{ product: Product; quantity: number } | null>(null);
  protected readonly purchasing = signal(false);
  protected readonly purchased = signal<Order | null>(null);

  ngOnInit(): void {
    this.loadProducts();
  }

  protected loadProducts(): void {
    this.loading.set(true);
    this.catalog.getProducts().subscribe({
      next: (products) => {
        this.products.set(products ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Error al cargar el catálogo desde el servidor.');
        this.loading.set(false);
      },
    });
  }

  protected openForm(product: Product | null): void {
    this.editing.set(product);
    this.showForm.set(true);
  }

  protected formClosed(changed: boolean): void {
    this.showForm.set(false);
    if (changed) this.loadProducts();
  }

  protected deleteProduct(product: Product): void {
    if (!window.confirm('¿Seguro que deseas eliminar este producto?')) return;
    this.catalog.deleteProduct(product.id).subscribe({
      next: () => this.products.update((list) => list.filter((p) => p.id !== product.id)),
      error: (err) => window.alert(apiErrorMessage(err, 'Error al eliminar producto')),
    });
  }

  protected startBuy(selection: { product: Product; quantity: number }): void {
    if (!this.auth.isAuthenticated()) {
      if (window.confirm('Para realizar una compra debe iniciar sesión con su cuenta. ¿Desea iniciar sesión ahora?')) {
        void this.router.navigateByUrl('/login');
      }
      return;
    }
    this.purchased.set(null);
    this.buying.set(selection);
  }

  protected confirmPurchase(): void {
    const selection = this.buying();
    if (!selection) return;
    this.purchasing.set(true);
    this.orders.purchase(selection.product, selection.quantity, this.customerId()).subscribe({
      next: (order) => {
        this.purchased.set(order);
        this.purchasing.set(false);
        this.loadProducts(); // refrescar stock en pantalla
      },
      error: (err) => {
        this.purchasing.set(false);
        window.alert(apiErrorMessage(err, 'Ocurrió un error al procesar la compra. Intente nuevamente.'));
      },
    });
  }

  protected closePurchase(): void {
    this.buying.set(null);
    this.purchased.set(null);
  }
}
