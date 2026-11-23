import { formatMoney } from './format';
import { filterOrders, kpis, monthlyRevenue, statusDistribution, topProducts } from './metrics';
import { Order } from './models';

const orders: Order[] = [
  {
    id: 1, customerId: 10, status: 'ENTREGADO', createdAt: '2026-11-02T10:00:00', total: 5980,
    items: [{ productId: 7, quantity: 2, unitPrice: 2990 }],
  },
  {
    id: 2, customerId: 11, status: 'CANCELADO', createdAt: '2026-10-15T10:00:00', total: 1000,
    items: [{ productId: 8, quantity: 1, unitPrice: 1000 }],
  },
  {
    id: 3, customerId: 12, status: 'CREADO', createdAt: '2026-11-20T10:00:00', total: 8970,
    items: [{ productId: 7, quantity: 3, unitPrice: 2990 }],
  },
];

describe('métricas de reportes', () => {
  it('filtra pedidos válidos y entregados', () => {
    expect(filterOrders(orders, 'ACTIVE').map((o) => o.id)).toEqual([1, 3]);
    expect(filterOrders(orders, 'DELIVERED').map((o) => o.id)).toEqual([1]);
    expect(filterOrders(orders, 'ALL')).toHaveLength(3);
  });

  it('calcula KPIs', () => {
    const k = kpis(filterOrders(orders, 'ACTIVE'));
    expect(k.totalRevenue).toBe(14950);
    expect(k.totalOrders).toBe(2);
    expect(k.avgTicket).toBe(7475);
    expect(k.totalUnitsSold).toBe(5);
  });

  it('agrupa ingresos de los últimos 6 meses del año en curso', () => {
    const bars = monthlyRevenue(orders, new Date('2026-11-23T12:00:00'));
    expect(bars.map((b) => b.month)).toEqual(['Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov']);
    expect(bars.at(-1)).toEqual({ month: 'Nov', value: 14950, percentage: 100 });
    expect(bars.at(-2)?.value).toBe(1000);
  });

  it('ordena los productos más vendidos por unidades', () => {
    const top = topProducts(orders, [{ id: 7, name: 'Caja', description: null, price: 2990, stock: 5 }]);
    expect(top[0]).toEqual({ id: 7, name: 'Caja', units: 5, revenue: 14950 });
    expect(top[1].name).toBe('Producto #8');
  });

  it('distribución por estado suma todos los pedidos', () => {
    const dist = statusDistribution(orders);
    expect(dist.reduce((sum, d) => sum + d.count, 0)).toBe(3);
    expect(dist.find((d) => d.status === 'ENTREGADO')?.percentage).toBe(33);
  });

  it('formatea montos en pesos chilenos', () => {
    expect(formatMoney(8970)).toBe('$8.970');
    expect(formatMoney(null)).toBe('$0');
  });
});
