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
    clientId:    import.meta.env.VITE_AZURE_AD_CLIENT_ID,
    authority:   `https://login.microsoftonline.com/${import.meta.env.VITE_AZURE_AD_TENANT_ID}/`,
    redirectUri: 'http://localhost:3000/auth/callback',
  },
  cache: {
    cacheLocation:        'sessionStorage', // sessionStorage: más seguro que localStorage
    storeAuthStateInCookie: false,
  },
};

/**
 * Scopes pedidos en cada login/token.
 * openid + profile  → claims básicos del usuario en el id_token.
 * access_as_user    → permiso delegado para llamar a la API (BFF).
 */
export const loginRequest = {
  scopes: [
    'openid',
    'profile',
    `api://${import.meta.env.VITE_API_CLIENT_ID}/access_as_user`,
  ],
};

export const msalInstance = new PublicClientApplication(msalConfig);
