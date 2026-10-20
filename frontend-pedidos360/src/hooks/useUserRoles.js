import { useMsal } from '@azure/msal-react';

const DEFAULT_ADMIN_EMAIL = 'gi.olavarria@duocuc.cl';

/**
 * resolveRoles
 * Determina de forma exhaustiva los roles de un usuario basándose en los claims
 * y propiedades de la cuenta retornada por Microsoft Entra ID (Azure AD).
 *
 * @param {object} account - Cuenta activa de MSAL
 * @returns {string[]} Lista de roles ('Admin', 'Operator', 'Customer')
 */
export function resolveRoles(account) {
  if (!account) return [];

  const claims = account.idTokenClaims || {};
  const rawRoles = [];

  // 1. Roles en claims.roles (estándar en Azure AD App Roles)
  if (claims.roles) {
    if (Array.isArray(claims.roles)) rawRoles.push(...claims.roles);
    else rawRoles.push(claims.roles);
  }

  // 2. Roles en claims.role (singular)
  if (claims.role) {
    if (Array.isArray(claims.role)) rawRoles.push(...claims.role);
    else rawRoles.push(claims.role);
  }

  // 3. Roles en esquema WS-Federation / SOAP claim
  const soapRole = claims['http://schemas.microsoft.com/ws/2008/06/identity/claims/role'];
  if (soapRole) {
    if (Array.isArray(soapRole)) rawRoles.push(...soapRole);
    else rawRoles.push(soapRole);
  }

  // 4. Correo del usuario (cuentas personales y corporativas de Microsoft)
  const userEmails = [
    account.username,
    claims.preferred_username,
    claims.email,
    claims.upn,
  ]
    .filter(Boolean)
    .map((e) => e.toLowerCase());

  // Correos con rol Administrador (VITE_ADMIN_EMAIL admite varios separados por coma).
  // IMPORTANTE: esto solo decide qué pantallas se muestran; el backend vuelve a validar
  // el rol con el token en cada petición, así que no es posible "hacerse admin" desde el navegador.
  const adminEmails = (import.meta.env.VITE_ADMIN_EMAIL || DEFAULT_ADMIN_EMAIL)
    .split(',')
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean);

  const hasAdminRole = rawRoles.some((r) => r === 'Admin');
  const isAdmin = hasAdminRole || userEmails.some((e) => adminEmails.includes(e));

  if (isAdmin) {
    return ['Admin', 'Operator', 'Customer'];
  }

  const hasOperatorRole = rawRoles.some((r) => r === 'Operator');

  if (hasOperatorRole) {
    return ['Operator', 'Customer'];
  }

  // Cualquier usuario autenticado que no sea Admin/Operator es Customer
  return ['Customer'];
}

/**
 * Hook para obtener los roles del usuario autenticado actualmente.
 * @returns {string[]} Array de roles del usuario, o [] si no hay sesión.
 */
function useUserRoles() {
  const { accounts } = useMsal();

  if (!accounts || accounts.length === 0) return [];

  return resolveRoles(accounts[0]);
}

export default useUserRoles;
