import apiClient from './apiClient';

const BASE = '/api/orders';

/**
 * Obtiene la lista completa de pedidos.
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function getOrders() {
  return apiClient.get(BASE);
}

/**
 * Obtiene un pedido por su ID.
 * @param {number|string} id
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function getOrderById(id) {
  return apiClient.get(`${BASE}/${id}`);
}

/**
 * Crea un nuevo pedido.
 * @param {Object} data - Cuerpo del pedido (customerId, items[])
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function createOrder(data) {
  return apiClient.post(BASE, data);
}

/**
 * Actualiza el estado de un pedido.
 * Estados válidos: CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO | CANCELADO
 * @param {number|string} id
 * @param {string} status
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function updateOrderStatus(id, status) {
  return apiClient.patch(`${BASE}/${id}/status`, null, { params: { status } });
}

/**
 * Actualiza un pedido completo (cliente e ítems).
 * @param {number|string} id
 * @param {Object} data
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function updateOrder(id, data) {
  return apiClient.put(`${BASE}/${id}`, data);
}
