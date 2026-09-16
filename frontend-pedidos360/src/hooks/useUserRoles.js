import { useMsal } from '@azure/msal-react';

/**
 * useUserRoles
 * Extrae el claim "roles" del idTokenClaims de la cuenta activa en MSAL.
 * Azure AD puebla este claim cuando el usuario tiene roles asignados
 * en el App Registration (roles: Admin, Operator, Customer).
 *
 * @returns {string[]} Array de roles del usuario, o [] si no hay sesión o no tiene roles.
 */
function useUserRoles() {
  const { accounts } = useMsal();

  if (accounts.length === 0) return [];

  const account = accounts[0];
  const claims = account?.idTokenClaims;
  const username = (account?.username || claims?.preferred_username || claims?.email || '').toLowerCase();
  const adminEmail = (import.meta.env.VITE_ADMIN_EMAIL || 'gi.olavarria@duocuc.cl').toLowerCase();

  const tokenRoles = claims?.roles
    ? (Array.isArray(claims.roles) ? claims.roles : [claims.roles])
    : [];

  const isAdmin = tokenRoles.includes('Admin') ||
                  (adminEmail && username === adminEmail);

  if (isAdmin) {
    return ['Admin', 'Operator', 'Customer'];
  }

  if (tokenRoles.includes('Operator')) {
    return ['Operator', 'Customer'];
  }

  // Cualquier usuario de Microsoft que ingrese y no sea admin es Cliente
  return ['Customer'];
}

export default useUserRoles;
