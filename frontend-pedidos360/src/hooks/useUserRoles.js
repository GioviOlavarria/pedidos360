import { useMsal } from '@azure/msal-react';

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

  // 4. Identificadores de usuario en minúsculas
  const username          = (account.username || '').toLowerCase();
  const preferredUsername = (claims.preferred_username || '').toLowerCase();
  const emailClaim        = (claims.email || '').toLowerCase();
  const upnClaim          = (claims.upn || '').toLowerCase();
  const uniqueNameClaim   = (claims.unique_name || '').toLowerCase();
  const nameClaim         = (account.name || claims.name || '').toLowerCase();

  const userIdentifiers = [
    username,
    preferredUsername,
    emailClaim,
    upnClaim,
    uniqueNameClaim,
    nameClaim,
  ].filter(Boolean);

  // Correos y patrones de administradores autorizados
  const KNOWN_ADMIN_IDENTIFIERS = [
    'gi.olavsanchez@gmail.com',
    'gi.olavsanchez',
    'olavsanchez',
    'gi.olavarria@duocuc.cl',
    (import.meta.env.VITE_ADMIN_EMAIL || '').toLowerCase(),
  ].filter(Boolean);

  // A) ¿Tiene rol 'Admin' explícito en Azure AD (insensible a mayúsculas)?
  const hasAdminRole = rawRoles.some(
    (r) => typeof r === 'string' && r.toLowerCase().includes('admin')
  );

  // B) ¿Coincide con alguno de los correos/patrones de administrador autorizados?
  const matchesAdminEmail = userIdentifiers.some(
    (id) => KNOWN_ADMIN_IDENTIFIERS.some((adminId) => id.includes(adminId))
  );

  // C) ¿Contiene palabra clave 'admin' o 'administrador'?
  const containsAdminKeyword = userIdentifiers.some(
    (id) => id.includes('admin') || id.includes('administrador')
  );

  // D) Override de desarrollo / prueba manual en sesión
  const sessionRole = typeof window !== 'undefined'
    ? (sessionStorage.getItem('pedidos360_override_role') || localStorage.getItem('pedidos360_override_role') || '').toLowerCase()
    : '';

  const isAdmin = hasAdminRole ||
                  matchesAdminEmail ||
                  containsAdminKeyword ||
                  sessionRole === 'admin';

  if (isAdmin) {
    return ['Admin', 'Operator', 'Customer'];
  }

  const hasOperatorRole = rawRoles.some(
    (r) => typeof r === 'string' && r.toLowerCase().includes('operator')
  ) || sessionRole === 'operator';

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
