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

  const claims = accounts[0]?.idTokenClaims;

  // El claim "roles" puede ser un array o un string único; normalizamos a array.
  if (!claims?.roles) return [];
  return Array.isArray(claims.roles) ? claims.roles : [claims.roles];
}

export default useUserRoles;
