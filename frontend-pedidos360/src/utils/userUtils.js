/**
 * userUtils.js
 * Utilidades para mapear usuarios de Microsoft a IDs numéricos de cliente requeridos por ms-pedidos360-orders.
 */

export function getCustomerIdFromAccount(account) {
  if (!account) return 101;
  const str = account.username || account.homeAccountId || account.name || 'customer';
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = ((hash << 5) - hash) + char;
    hash |= 0;
  }
  return Math.abs(hash % 9000) + 1000;
}

export function formatMoney(val) {
  return '$' + Number(val || 0).toLocaleString('es-CL', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}
