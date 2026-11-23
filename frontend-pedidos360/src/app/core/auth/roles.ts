import { AccountInfo } from '@azure/msal-browser';
import { Role } from '../models';

function asList(value: unknown): string[] {
  if (value == null) return [];
  return (Array.isArray(value) ? value : [value]).map(String);
}

/**
 * Roles del usuario según los claims del ID token de Microsoft Entra ID.
 *
 * IMPORTANTE: solo decide qué pantallas se muestran. El backend vuelve a validar el rol con el JWT en
 * cada petición, así que no es posible "hacerse admin" desde el navegador.
 *
 * @param adminEmails correos (separados por coma) que entran como Administrador
 */
export function resolveRoles(account: AccountInfo | null | undefined, adminEmails: string): Role[] {
  if (!account) return [];

  const claims = (account.idTokenClaims ?? {}) as Record<string, unknown>;
  const rawRoles = [
    ...asList(claims['roles']),
    ...asList(claims['role']),
    ...asList(claims['http://schemas.microsoft.com/ws/2008/06/identity/claims/role']),
  ];

  const userEmails = [account.username, claims['preferred_username'], claims['email'], claims['upn']]
    .filter(Boolean)
    .map((email) => String(email).toLowerCase());

  const admins = adminEmails
    .split(',')
    .map((email) => email.trim().toLowerCase())
    .filter(Boolean);

  if (rawRoles.includes('Admin') || userEmails.some((email) => admins.includes(email))) {
    return ['Admin', 'Operator', 'Customer'];
  }
  if (rawRoles.includes('Operator')) {
    return ['Operator', 'Customer'];
  }
  // Cualquier usuario autenticado que no sea Admin/Operator es Customer.
  return ['Customer'];
}
