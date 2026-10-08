import { PublicClientApplication } from '@azure/msal-browser';

/**
 * Configuración de MSAL para Azure AD.
 * Variables de entorno requeridas (definidas en .env.local):
 *   VITE_AZURE_AD_CLIENT_ID   → App Registration del frontend
 *   VITE_AZURE_AD_TENANT_ID   → Tenant de Azure AD
 *   VITE_API_CLIENT_ID        → App Registration del BFF/API (para el scope)
 */
export const msalConfig = {
  auth: {
    clientId:    import.meta.env.VITE_AZURE_AD_CLIENT_ID || 'a88830c8-ace4-45af-8710-18aec6f1c060',
    authority:   'https://login.microsoftonline.com/common/',
    redirectUri: typeof window !== 'undefined' ? `${window.location.origin}/auth/callback` : 'http://localhost:3000/auth/callback',
  },
  cache: {
    cacheLocation:        'sessionStorage', // sessionStorage: más seguro que localStorage
    storeAuthStateInCookie: false,
  },
};

/**
 * Scopes pedidos SOLO para el login (autenticación).
 * NO incluir scopes de API aquí — algunos tenants organizacionales
 * requieren admin consent para API scopes y bloquean el login.
 */
export const loginRequest = {
  scopes: ['openid', 'profile', 'email'],
};

/**
 * Scopes para obtener token de acceso a la API (BFF).
 * Se solicita DESPUÉS del login, al hacer llamadas a la API.
 */
export const apiTokenRequest = {
  scopes: [`api://${import.meta.env.VITE_API_CLIENT_ID}/access_as_user`],
};

export const msalInstance = new PublicClientApplication(msalConfig);
