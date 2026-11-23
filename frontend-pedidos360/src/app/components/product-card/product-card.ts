import { Component, computed, input, output, signal } from '@angular/core';
import { Product } from '../../core/models';
import { MoneyPipe } from '../../shared/money.pipe';

/** Tarjeta de producto: el Admin edita o elimina; el cliente elige cantidad y compra. */
@Component({
  selector: 'app-product-card',
  imports: [MoneyPipe],
  templateUrl: './product-card.html',
  styleUrl: './product-card.css',
})
export class ProductCard {
  readonly product = input.required<Product>();
  readonly isAdmin = input(false);

  readonly edit = output<Product>();
  readonly remove = output<Product>();
  readonly buy = output<{ product: Product; quantity: number }>();

  protected readonly quantity = signal(1);

  protected readonly stock = computed(() => {
    const stock = this.product().stock;
    if (stock > 10) return { text: 'En stock', color: '#15803d', background: '#dcfce7' };
    if (stock > 0) return { text: `Poco stock (${stock})`, color: '#b45309', background: '#fef3c7' };
    return { text: 'Agotado', color: '#b91c1c', background: '#fee2e2' };
  });

  protected decrease(): void {
    this.quantity.update((q) => Math.max(1, q - 1));
  }

  protected increase(): void {
    this.quantity.update((q) => Math.min(this.product().stock, q + 1));
  }
}
