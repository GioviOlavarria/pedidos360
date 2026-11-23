import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CatalogService } from '../../core/api/catalog.service';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart.service';
import { apiErrorMessage } from '../../core/format';
import { Product } from '../../core/models';
import { ProductCard } from '../../components/product-card/product-card';
import { ProductForm } from '../../components/product-form/product-form';

/** Catálogo: público para ver, carrito para clientes y administración de inventario para el Admin. */
@Component({
  selector: 'app-catalog-page',
  imports: [RouterLink, ProductCard, ProductForm],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.css',
})
export class CatalogPage implements OnInit {
  private readonly catalog = inject(CatalogService);
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly products = signal<Product[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  /** Aviso breve al agregar al carrito. */
  protected readonly added = signal('');

  // Administración
  protected readonly showForm = signal(false);
  protected readonly editing = signal<Product | null>(null);

  private addedTimer?: ReturnType<typeof setTimeout>;

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

  protected addToCart(selection: { product: Product; quantity: number }): void {
    this.cart.add(selection.product, selection.quantity);
    this.added.set(`${selection.quantity} × ${selection.product.name} agregado al carrito`);
    clearTimeout(this.addedTimer);
    this.addedTimer = setTimeout(() => this.added.set(''), 3000);
  }
}
