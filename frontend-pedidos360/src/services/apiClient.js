import axios from 'axios';
import { InteractionRequiredAuthError } from '@azure/msal-browser';
import { msalInstance, loginRequest } from '../authConfig';

/**
 * ─── ¿Cómo se usa msalInstance fuera de React? ────────────────────────────────
 *
 * Los hooks de MSAL (useMsal, useIsAuthenticated, etc.) sólo funcionan dentro
 * del árbol de componentes React. Los servicios como este archivo son módulos
 * JS planos que se ejecutan fuera de ese árbol, por lo que no pueden usar hooks.
 *
 * Solución: exportar la instancia de PublicClientApplication (PCA) directamente
 * desde authConfig.js como un singleton de módulo ES. El mismo objeto es el que
 * recibe MsalProvider en main.jsx, por lo que comparten el mismo cache de tokens.
 * Esto es el patrón oficial recomendado por Microsoft para llamadas fuera de React:
 * https://github.com/AzureAD/microsoft-authentication-library-for-js/blob/dev/lib/msal-react/docs/getting-started.md#acquiring-an-access-token-outside-of-a-react-component
 * ──────────────────────────────────────────────────────────────────────────────
 */

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_BFF_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Interceptor de request: adjunta el Bearer token en cada petición.
 *
 * Flujo:
 *  1. Intenta acquireTokenSilent (devuelve token del cache sin interacción).
 *  2. Si el token expiró o no está en cache (InteractionRequiredAuthError),
 *     hace fallback a acquireTokenRedirect que redirige al login de Azure AD.
 *  3. Si hay otro error inesperado, lanza para que el caller lo maneje.
 */
apiClient.interceptors.request.use(async (config) => {
  const accounts = msalInstance.getAllAccounts();

  // Si no hay sesión activa, dejar pasar la petición sin token.
  // ProtectedRoute ya garantiza que el usuario está autenticado antes
  // de que cualquier página pueda llamar a los servicios.
  if (accounts.length === 0) return config;

  const tokenRequest = {
    ...loginRequest,
    account: accounts[0],
  };

  try {
    const response = await msalInstance.acquireTokenSilent(tokenRequest);
    config.headers['Authorization'] = `Bearer ${response.accessToken}`;
  } catch (error) {
    if (error instanceof InteractionRequiredAuthError) {
      // Token expirado o revocado: redirige al login de Azure AD.
      // acquireTokenRedirect no retorna (navega fuera), por eso no
      // es necesario hacer return aquí; la petición quedará pendiente.
      await msalInstance.acquireTokenRedirect(tokenRequest);
    } else {
      // Error inesperado (red, configuración): propagar para debugging.
      throw error;
    }
  }

  return config;
});

export default apiClient;
