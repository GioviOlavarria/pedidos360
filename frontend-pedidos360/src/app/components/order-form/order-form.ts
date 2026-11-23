import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CatalogService } from '../../core/api/catalog.service';
import { OrdersService } from '../../core/api/orders.service';
import { apiErrorMessage } from '../../core/format';
import { Order, Product } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

interface Line {
  product: Product;
  quantity: number;
}

/**
 * Modal del Admin para crear o editar un pedido. El backend recalcula precios y total con el catálogo
 * y reserva el stock; el total que se muestra aquí es solo referencial.
 */
@Component({
  selector: 'app-order-form',
  imports: [FormsModule, MoneyPipe],
  templateUrl: './order-form.html',
  styles: `
    .lines {
      margin-bottom: 20px;
      background: var(--slate-50);
      padding: 16px;
      border-radius: 8px;
    }
    .lines h4 {
      margin: 0 0 12px;
      font-size: 0.9rem;
      color: var(--navy);
    }
    .line {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
      gap: 8px;
    }
    .line > span {
      flex: 1;
      font-size: 0.85rem;
      color: var(--slate-600);
    }
    .line .p-input {
      width: 70px;
      padding: 4px 8px;
    }
    .remove {
      color: #dc2626;
      background: none;
      border: none;
      cursor: pointer;
    }
    .total {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid var(--slate-300);
      text-align: right;
      font-weight: bold;
      color: var(--navy);
    }
    .p-modal-actions {
      margin-top: 32px;
    }
  `,
})
export class OrderForm implements OnInit {
  private readonly catalog = inject(CatalogService);
  private readonly orders = inject(OrdersService);

  readonly order = input<Order | null>(null);
  readonly closed = output<boolean>();

  protected readonly products = signal<Product[]>([]);
  protected readonly lines = signal<Line[]>([]);
  protected readonly customerId = signal<number | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly total = computed(() => this.lines().reduce((sum, l) => sum + l.product.price * l.quantity, 0));

  ngOnInit(): void {
    this.catalog.getProducts().subscribe({
      next: (products) => {
        this.products.set(products);
        const order = this.order();
        if (order) {
          this.customerId.set(order.customerId);
          this.lines.set(
            (order.items ?? []).map((item) => ({
              product: products.find((p) => p.id === item.productId) ?? {
                id: item.productId,
                name: item.productName || `Producto ${item.productId}`,
                description: null,
                price: item.unitPrice,
                stock: 999,
              },
              quantity: item.quantity,
            })),
          );
        }
      },
      error: () => this.error.set('No se pudo cargar el catálogo.'),
    });
  }

  protected addProduct(select: HTMLSelectElement): void {
    const product = this.products().find((p) => p.id === Number(select.value));
    select.value = '';
    if (!product) return;
    this.lines.update((lines) =>
      lines.some((l) => l.product.id === product.id)
        ? lines.map((l) => (l.product.id === product.id ? { ...l, quantity: l.quantity + 1 } : l))
        : [...lines, { product, quantity: 1 }],
    );
  }

  protected setQuantity(productId: number, value: number): void {
    if (!Number.isInteger(value) || value < 1) return;
    this.lines.update((lines) => lines.map((l) => (l.product.id === productId ? { ...l, quantity: value } : l)));
  }

  protected removeLine(productId: number): void {
    this.lines.update((lines) => lines.filter((l) => l.product.id !== productId));
  }

  protected submit(): void {
    const customerId = this.customerId();
    if (!customerId || this.lines().length === 0) {
      this.error.set('Ingrese un cliente y al menos un producto.');
      return;
    }
    const payload = {
      customerId,
      total: this.total(),
      items: this.lines().map((l) => ({ productId: l.product.id, quantity: l.quantity, unitPrice: l.product.price })),
    };
    const order = this.order();
    const request = order ? this.orders.updateOrder(order.id, payload) : this.orders.createOrder(payload);

    this.saving.set(true);
    this.error.set('');
    request.subscribe({
      next: () => this.closed.emit(true),
      error: (err) => {
        this.error.set(apiErrorMessage(err, `Error al ${order ? 'actualizar' : 'crear'} el pedido.`));
        this.saving.set(false);
      },
    });
  }
}
