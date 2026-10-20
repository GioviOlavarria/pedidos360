import { PublicClientApplication, EventType } from '@azure/msal-browser';

/**
 * Configuración de MSAL para Azure AD.
 * Variables de entorno requeridas (definidas en .env.local):
 *   VITE_AZURE_AD_CLIENT_ID   → App Registration del frontend
 *   VITE_AZURE_AD_TENANT_ID   → Tenant de Azure AD
 *   VITE_API_CLIENT_ID        → App Registration del BFF/API (para el scope)
 */
const TENANT_ID = import.meta.env.VITE_AZURE_AD_TENANT_ID || '868cd46e-03d0-4527-acf4-0363548bd6c6';

/**
 * Authority del tenant propio. Las cuentas de organizaciones que bloquean apps externas
 * (p. ej. DuocUC) inician sesión aquí como INVITADAS del tenant, donde el consentimiento
 * lo controla nuestro administrador y no el de su organización.
 */
export const TENANT_AUTHORITY = `https://login.microsoftonline.com/${TENANT_ID}/`;

/**
 * Authority con la que se obtuvo la sesión de la cuenta: los tokens de la API deben pedirse
 * al mismo tenant, o Microsoft volvería a enviar al usuario a su organización de origen.
 */
export function authorityForAccount(account) {
  return account?.tenantId
    ? `https://login.microsoftonline.com/${account.tenantId}/`
    : msalConfig.auth.authority;
}

export const msalConfig = {
  auth: {
    clientId:    import.meta.env.VITE_AZURE_AD_CLIENT_ID || 'a88830c8-ace4-45af-8710-18aec6f1c060',
    authority:   'https://login.microsoftonline.com/common/',
    redirectUri: typeof window !== 'undefined' ? `${window.location.origin}/auth/callback` : 'http://localhost:3000/auth/callback',
    // Tras el login se queda en /auth/callback y AuthCallbackPage decide el destino según el rol
    // (sin esto MSAL recarga la página donde empezó el login y se pierde el resultado).
    navigateToLoginRequestUrl: false,
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
  // Sin 'email': el correo ya llega en preferred_username. Pedir scopes nuevos obliga a un
  // consentimiento nuevo, que tenants como DuocUC bloquean ("Aprobación necesaria").
  scopes: ['openid', 'profile'],
};

/** Login para cuentas institucionales invitadas en el tenant (DuocUC u otras). */
export const institutionalLoginRequest = {
  ...loginRequest,
  authority: TENANT_AUTHORITY,
  prompt: 'select_account',
};

/**
 * Scopes para obtener token de acceso a la API (BFF).
 * Se solicita DESPUÉS del login, al hacer llamadas a la API.
 */
export const apiTokenRequest = {
  scopes: [`api://${import.meta.env.VITE_API_CLIENT_ID}/access_as_user`],
};

export const msalInstance = new PublicClientApplication(msalConfig);

const AUTH_ERROR_KEY = 'pedidos360_auth_error';

/**
 * Se registra antes de que MsalProvider procese la respuesta de Microsoft, para no perder
 * ningún evento: fija la cuenta activa al iniciar sesión y guarda el error si el login falla,
 * para mostrarlo en la página de login (en vez de volver al inicio sin explicación).
 */
msalInstance.addEventCallback((event) => {
  if (event.eventType === EventType.LOGIN_SUCCESS && event.payload?.account) {
    msalInstance.setActiveAccount(event.payload.account);
    sessionStorage.removeItem(AUTH_ERROR_KEY);
  }
  if (event.eventType === EventType.LOGIN_FAILURE && event.error) {
    const { errorCode, errorMessage, message } = event.error;
    const detail = (errorMessage || message || '').split(/\r?\n/)[0];
    sessionStorage.setItem(AUTH_ERROR_KEY, `${errorCode || 'error'}: ${detail}`);
  }
});

/** Devuelve (y borra) el último error de inicio de sesión, si lo hay. */
export function consumeAuthError() {
  const error = sessionStorage.getItem(AUTH_ERROR_KEY);
  sessionStorage.removeItem(AUTH_ERROR_KEY);
  return error;
}
