import { OrderStatus } from './models';

export const STATUS_LABELS: Record<OrderStatus, string> = {
  CREADO: 'Creado',
  ACEPTADO: 'Aceptado',
  EN_PREPARACION: 'En preparación',
  DESPACHADO: 'Despachado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

/** Colores de etiqueta (fondo / texto) por estado. */
export const STATUS_STYLES: Record<OrderStatus, { background: string; color: string }> = {
  CREADO: { background: '#dbeafe', color: '#1d4ed8' },
  ACEPTADO: { background: '#ede9fe', color: '#6d28d9' },
  EN_PREPARACION: { background: '#fef9c3', color: '#854d0e' },
  DESPACHADO: { background: '#ffedd5', color: '#c2410c' },
  ENTREGADO: { background: '#dcfce7', color: '#15803d' },
  CANCELADO: { background: '#fee2e2', color: '#b91c1c' },
};

/** Colores de los gráficos de distribución por estado. */
export const STATUS_CHART_COLORS: Record<OrderStatus, string> = {
  CREADO: '#3b82f6',
  ACEPTADO: '#8b5cf6',
  EN_PREPARACION: '#f59e0b',
  DESPACHADO: '#f97316',
  ENTREGADO: '#10b981',
  CANCELADO: '#ef4444',
};

/** Flujo normal del pedido (CANCELADO puede ocurrir desde cualquier paso previo a ENTREGADO). */
export const STATUS_STEPS: OrderStatus[] = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO'];

export const NEXT_STATUS: Record<OrderStatus, OrderStatus | null> = {
  CREADO: 'ACEPTADO',
  ACEPTADO: 'EN_PREPARACION',
  EN_PREPARACION: 'DESPACHADO',
  DESPACHADO: 'ENTREGADO',
  ENTREGADO: null,
  CANCELADO: null,
};

export function statusLabel(status: string): string {
  return STATUS_LABELS[status as OrderStatus] ?? status;
}
