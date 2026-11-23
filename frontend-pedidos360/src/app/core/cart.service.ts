import { Injectable, computed, effect, signal } from '@angular/core';
import { CartItem, Product } from './models';

const STORAGE_KEY = 'pedidos360_cart';

/**
 * Carrito de compras del navegador (signals). Se guarda en localStorage para no perderlo al recargar o al
 * volver de la pasarela de pago. Los precios son referenciales: el backend recalcula el total con el catálogo.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly itemsState = signal<CartItem[]>(load());

  readonly items = this.itemsState.asReadonly();
  readonly count = computed(() => this.itemsState().reduce((sum, i) => sum + i.quantity, 0));
  readonly total = computed(() => this.itemsState().reduce((sum, i) => sum + i.price * i.quantity, 0));

  constructor() {
    effect(() => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(this.itemsState()));
      } catch {
        // Almacenamiento no disponible (modo privado): el carrito vive solo en memoria.
      }
    });
  }

  /** Agrega unidades sin superar el stock disponible. */
  add(product: Product, quantity = 1): void {
    this.itemsState.update((items) => {
      const existing = items.find((i) => i.productId === product.id);
      if (existing) {
        return items.map((i) =>
          i.productId === product.id
            ? { ...i, price: product.price, stock: product.stock, quantity: Math.min(product.stock, i.quantity + quantity) }
            : i,
        );
      }
      return [
        ...items,
        { productId: product.id, name: product.name, price: product.price, stock: product.stock,
          quantity: Math.min(product.stock, quantity) },
      ];
    });
  }

  setQuantity(productId: number, quantity: number): void {
    this.itemsState.update((items) =>
      items.map((i) => (i.productId === productId ? { ...i, quantity: Math.max(1, Math.min(i.stock, quantity)) } : i)),
    );
  }

  remove(productId: number): void {
    this.itemsState.update((items) => items.filter((i) => i.productId !== productId));
  }

  clear(): void {
    this.itemsState.set([]);
  }
}

function load(): CartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
