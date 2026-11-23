import {
  AccountInfo,
  BrowserCacheLocation,
  Configuration,
  PublicClientApplication,
  RedirectRequest,
  SilentRequest,
} from '@azure/msal-browser';
import { environment } from '../../../environments/environment';

/**
 * Configuración de MSAL para Microsoft Entra ID (Azure AD), el IDaaS de Pedidos360.
 * Valores en src/environments/environment.ts (generado por scripts/set-env.mjs).
 */

/**
 * Authority del tenant propio. Las cuentas de organizaciones que bloquean apps externas
 * (p. ej. DuocUC) inician sesión aquí como INVITADAS del tenant, donde el consentimiento
 * lo controla nuestro administrador y no el de su organización.
 */
export const TENANT_AUTHORITY = `https://login.microsoftonline.com/${environment.azureAdTenantId}/`;

export const msalConfig: Configuration = {
  auth: {
    clientId: environment.azureAdClientId,
    authority: 'https://login.microsoftonline.com/common/',
    redirectUri: `${window.location.origin}/auth/callback`,
  },
  cache: {
    cacheLocation: BrowserCacheLocation.SessionStorage, // más seguro que localStorage
  },
};

/**
 * Scopes pedidos SOLO para el login. Sin scopes de API ni 'email': pedir scopes nuevos obliga a un
 * consentimiento nuevo, que tenants como DuocUC bloquean ("Aprobación necesaria").
 */
export const loginRequest: RedirectRequest = {
  scopes: ['openid', 'profile'],
};

/** Login para cuentas institucionales invitadas en el tenant (DuocUC u otras). */
export const institutionalLoginRequest: RedirectRequest = {
  ...loginRequest,
  authority: TENANT_AUTHORITY,
  prompt: 'select_account',
};

/** Scope del access token (JWT) para llamar al BFF. Se pide después del login. */
export const API_SCOPES = [`api://${environment.apiClientId}/access_as_user`];

/**
 * Authority con la que se obtuvo la sesión de la cuenta: los tokens de la API deben pedirse al mismo
 * tenant, o Microsoft volvería a enviar al usuario a su organización de origen.
 */
export function authorityForAccount(account: AccountInfo): string {
  return account.tenantId ? `https://login.microsoftonline.com/${account.tenantId}/` : msalConfig.auth.authority!;
}

export function apiTokenRequest(account: AccountInfo): SilentRequest {
  return { scopes: API_SCOPES, account, authority: authorityForAccount(account) };
}

/** Instancia única de MSAL (la usan AuthService y el interceptor HTTP). */
export const msalInstance = new PublicClientApplication(msalConfig);
