/** Contratos de la API (BFF → catalog / orders). */

export type Role = 'Admin' | 'Operator' | 'Customer';

export type OrderStatus = 'PENDIENTE_PAGO' | 'CREADO' | 'ACEPTADO' | 'EN_PREPARACION' | 'DESPACHADO' | 'ENTREGADO' | 'CANCELADO';

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
  /** Pago confirmado (Flow o manual del Admin). */
  paidAt?: string | null;
  paymentReference?: string | null;
  /** Boleta electrónica SII registrada vía Kafka. */
  receiptFolio?: number | null;
  receiptTrackId?: string | null;
  receiptSiiStatus?: string | null;
  receiptIssuedAt?: string | null;
}

export type PaymentStatus = 'PENDING' | 'PAID' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';

/** Intento de pago en la pasarela (ms-pedidos360-payments). */
export interface Payment {
  id: number;
  orderId: number;
  status: PaymentStatus;
  amount: number;
  provider: 'FLOW' | 'SIMULADO';
  providerReference: string | null;
  paymentMethod: string | null;
  redirectUrl: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface CartItem {
  productId: number;
  name: string;
  price: number;
  stock: number;
  quantity: number;
}

export interface OrderRequest {
  customerId: number;
  total: number;
  items: { productId: number; quantity: number; unitPrice: number }[];
}
