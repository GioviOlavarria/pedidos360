import { TestBed } from '@angular/core/testing';
import { CartService } from './cart.service';
import { filterOrders } from './metrics';
import { Product } from './models';

const caja: Product = { id: 7, name: 'Caja', description: null, price: 2990, stock: 3 };
const cinta: Product = { id: 8, name: 'Cinta', description: null, price: 1490, stock: 10 };

describe('CartService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('suma cantidades del mismo producto sin superar el stock', () => {
    const cart = TestBed.inject(CartService);
    cart.add(caja, 2);
    cart.add(caja, 5);
    expect(cart.items()).toHaveLength(1);
    expect(cart.items()[0].quantity).toBe(3);
  });

  it('calcula unidades y total', () => {
    const cart = TestBed.inject(CartService);
    cart.add(caja, 2);
    cart.add(cinta, 1);
    expect(cart.count()).toBe(3);
    expect(cart.total()).toBe(2990 * 2 + 1490);
    cart.setQuantity(8, 0); // mínimo 1
    expect(cart.items()[1].quantity).toBe(1);
    cart.remove(7);
    expect(cart.total()).toBe(1490);
  });

  it('persiste el carrito en localStorage', () => {
    const cart = TestBed.inject(CartService);
    cart.add(cinta, 4);
    TestBed.tick();
    expect(JSON.parse(localStorage.getItem('pedidos360_cart') ?? '[]')[0].quantity).toBe(4);
  });
});

describe('pedidos impagos en reportes', () => {
  it('no cuentan como pedidos válidos', () => {
    const orders = [
      { id: 1, customerId: 1, status: 'PENDIENTE_PAGO', createdAt: '', total: 100 },
      { id: 2, customerId: 1, status: 'CREADO', createdAt: '', total: 200 },
    ] as const;
    expect(filterOrders([...orders], 'ACTIVE').map((o) => o.id)).toEqual([2]);
  });
});
