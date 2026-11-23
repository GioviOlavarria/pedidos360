/** Contratos de la API (BFF → catalog / orders). */

export type Role = 'Admin' | 'Operator' | 'Customer';

export type OrderStatus = 'CREADO' | 'ACEPTADO' | 'EN_PREPARACION' | 'DESPACHADO' | 'ENTREGADO' | 'CANCELADO';

export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  stock: number;
}

export interface ProductRequest {
  name: string;
  description: string;
  price: number;
  stock: number;
}

/** Ventas por producto calculadas por catalog con los eventos Kafka (solo Admin). */
export interface ProductSales {
  productId: number;
  productName: string | null;
  unitsSold: number;
  revenue: number;
  orders: number;
}

export interface OrderItem {
  id?: number;
  productId: number;
  productName?: string | null;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: number;
  customerId: number;
  status: OrderStatus;
  createdAt: string;
  total: number;
  customerName?: string | null;
  items?: OrderItem[];
  /** Boleta electrónica SII registrada vía Kafka. */
  receiptFolio?: number | null;
  receiptTrackId?: string | null;
  receiptSiiStatus?: string | null;
  receiptIssuedAt?: string | null;
}

export interface OrderRequest {
  customerId: number;
  total: number;
  items: { productId: number; quantity: number; unitPrice: number }[];
}
