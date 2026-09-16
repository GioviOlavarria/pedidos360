import apiClient from './apiClient';

const BASE = '/api/catalog';

/**
 * Obtiene la lista completa de productos.
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function getProducts() {
  return apiClient.get(BASE);
}

/**
 * Obtiene un producto por su ID.
 * @param {number|string} id
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function getProductById(id) {
  return apiClient.get(`${BASE}/${id}`);
}

/**
 * Crea un nuevo producto.
 * @param {Object} data - { name, description, price, stock }
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function createProduct(data) {
  return apiClient.post(BASE, data);
}

/**
 * Actualiza los datos de un producto existente.
 * @param {number|string} id
 * @param {Object} data - Campos a actualizar
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function updateProduct(id, data) {
  return apiClient.put(`${BASE}/${id}`, data);
}

/**
 * Disminuye el stock de un producto.
 * @param {number|string} id
 * @param {number} quantity - Cantidad a descontar
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function decreaseStock(id, quantity) {
  return apiClient.patch(`${BASE}/${id}/stock`, null, { params: { quantity } });
}

/**
 * Elimina un producto.
 * @param {number|string} id
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function deleteProduct(id) {
  return apiClient.delete(`${BASE}/${id}`);
}
