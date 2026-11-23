import { STATUS_CHART_COLORS, STATUS_LABELS } from './order-status';
import { Order, OrderStatus, Product } from './models';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

export type ReportFilter = 'ACTIVE' | 'DELIVERED' | 'ALL';

export function filterOrders(orders: Order[], filter: ReportFilter): Order[] {
  if (filter === 'DELIVERED') return orders.filter((o) => o.status === 'ENTREGADO');
  if (filter === 'ACTIVE') return orders.filter((o) => o.status !== 'CANCELADO');
  return orders;
}

/** Porcentaje entero de `count` sobre `total` (0 si no hay pedidos). */
export function percent(count: number, total: number): number {
  return total > 0 ? Math.round((count / total) * 100) : 0;
}

export function countByStatus(orders: Order[], status: OrderStatus): number {
  return orders.filter((o) => o.status === status).length;
}

export interface Kpis {
  totalRevenue: number;
  totalOrders: number;
  avgTicket: number;
  totalUnitsSold: number;
}

export function kpis(orders: Order[]): Kpis {
  const totalRevenue = orders.reduce((sum, o) => sum + (Number(o.total) || 0), 0);
  const totalUnitsSold = orders.reduce(
    (sum, o) => sum + (o.items ?? []).reduce((units, i) => units + (Number(i.quantity) || 0), 0),
    0,
  );
  return {
    totalRevenue,
    totalOrders: orders.length,
    avgTicket: orders.length > 0 ? totalRevenue / orders.length : 0,
    totalUnitsSold,
  };
}

export interface MonthBar {
  month: string;
  value: number;
  percentage: number;
}

/** Ingresos de los últimos 6 meses del año en curso (barras relativas al mes con más ingresos). */
export function monthlyRevenue(orders: Order[], now = new Date()): MonthBar[] {
  const revenue = Array<number>(12).fill(0);
  for (const order of orders) {
    const date = new Date(order.createdAt);
    if (!isNaN(date.getTime()) && date.getFullYear() === now.getFullYear()) {
      revenue[date.getMonth()] += Number(order.total) || 0;
    }
  }
  const current = now.getMonth();
  const start = Math.max(0, current - 5);
  const max = Math.max(...revenue.slice(start, current + 1)) || 1;
  return revenue.slice(start, current + 1).map((value, i) => ({
    month: MONTHS[start + i],
    value,
    percentage: Math.round((value / max) * 100),
  }));
}

export interface TopProduct {
  id: number;
  name: string;
  units: number;
  revenue: number;
}

export function topProducts(orders: Order[], products: Product[], limit = 5): TopProduct[] {
  const byProduct = new Map<number, TopProduct>();
  for (const order of orders) {
    for (const item of order.items ?? []) {
      const current = byProduct.get(item.productId) ?? {
        id: item.productId,
        name:
          products.find((p) => p.id === item.productId)?.name ?? item.productName ?? `Producto #${item.productId}`,
        units: 0,
        revenue: 0,
      };
      const quantity = Number(item.quantity) || 0;
      current.units += quantity;
      current.revenue += quantity * (Number(item.unitPrice) || 0);
      byProduct.set(item.productId, current);
    }
  }
  return [...byProduct.values()].sort((a, b) => b.units - a.units).slice(0, limit);
}

export interface StatusShare {
  status: OrderStatus;
  label: string;
  count: number;
  color: string;
  percentage: number;
}

export function statusDistribution(orders: Order[]): StatusShare[] {
  return (Object.keys(STATUS_LABELS) as OrderStatus[]).map((status) => {
    const count = countByStatus(orders, status);
    return {
      status,
      label: STATUS_LABELS[status],
      count,
      color: STATUS_CHART_COLORS[status],
      percentage: percent(count, orders.length),
    };
  });
}
