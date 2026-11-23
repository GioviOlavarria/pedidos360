import { HttpErrorResponse } from '@angular/common/http';
import { AccountInfo } from '@azure/msal-browser';

/** Monto en pesos chilenos: $8.970 / $1.234,5 */
export function formatMoney(value: number | string | null | undefined): string {
  return (
    '$' +
    Number(value || 0).toLocaleString('es-CL', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    })
  );
}

/**
 * ID numérico de cliente que pide ms-pedidos360-orders, derivado de la cuenta de Microsoft.
 * Es estable para la misma cuenta. (El backend identifica al comprador por el correo del token.)
 */
export function customerIdFromAccount(account: AccountInfo | null | undefined): number {
  if (!account) return 101;
  const str = account.username || account.homeAccountId || account.name || 'customer';
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash % 9000) + 1000;
}

/** Mensaje de error del backend (campo "message") o un texto por defecto. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse && typeof error.error?.message === 'string') {
    return error.error.message;
  }
  return fallback;
}
