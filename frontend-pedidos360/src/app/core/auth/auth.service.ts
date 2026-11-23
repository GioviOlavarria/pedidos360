import { Injectable, computed, signal } from '@angular/core';
import { AccountInfo, AuthenticationResult, EventType, IPublicClientApplication } from '@azure/msal-browser';
import { environment } from '../../../environments/environment';
import { Role } from '../models';
import { institutionalLoginRequest, loginRequest, msalInstance } from './msal.config';
import { resolveRoles } from './roles';

const AUTH_ERROR_KEY = 'pedidos360_auth_error';

/**
 * Sesión del usuario con Microsoft Entra ID (IDaaS) expuesta como signals.
 * {@link initialize} se ejecuta al arrancar la app (provideAppInitializer), antes de que el router evalúe
 * los guards: así la respuesta del login (redirect a /auth/callback) ya está procesada cuando se navega.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly msal: IPublicClientApplication = msalInstance;
  private readonly accountState = signal<AccountInfo | null>(null);

  readonly account = this.accountState.asReadonly();
  readonly isAuthenticated = computed(() => this.accountState() !== null);
  readonly roles = computed<Role[]>(() => resolveRoles(this.accountState(), environment.adminEmails));
  readonly isAdmin = computed(() => this.roles().includes('Admin'));
  readonly displayName = computed(() => {
    const account = this.accountState();
    return account?.name || account?.username || 'Invitado';
  });

  async initialize(): Promise<void> {
    await this.msal.initialize();

    // Se registra antes de procesar la respuesta de Microsoft para no perder el evento.
    this.msal.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
        this.msal.setActiveAccount((event.payload as AuthenticationResult).account);
        sessionStorage.removeItem(AUTH_ERROR_KEY);
      }
    });

    try {
      // Tras el login se queda en /auth/callback (no vuelve a la página donde empezó) y
      // AuthCallbackPage decide el destino según el rol.
      const result = await this.msal.handleRedirectPromise({ navigateToLoginRequestUrl: false });
      if (result?.account) {
        this.msal.setActiveAccount(result.account);
      }
    } catch (error) {
      // Login rechazado (p. ej. consentimiento bloqueado por la organización): se muestra en /login.
      const { errorCode, errorMessage, message } = error as { errorCode?: string; errorMessage?: string; message?: string };
      const detail = (errorMessage || message || '').split(/\r?\n/)[0];
      sessionStorage.setItem(AUTH_ERROR_KEY, `${errorCode || 'error'}: ${detail}`);
      console.error('Error procesando la respuesta de Microsoft Entra ID', error);
    }
    this.refresh();
  }

  /** Cuenta activa (o la primera en caché). */
  currentAccount(): AccountInfo | null {
    return this.msal.getActiveAccount() ?? this.msal.getAllAccounts()[0] ?? null;
  }

  refresh(): void {
    this.accountState.set(this.currentAccount());
  }

  hasAnyRole(allowed: readonly Role[]): boolean {
    return allowed.length === 0 || this.roles().some((role) => allowed.includes(role));
  }

  login(): Promise<void> {
    return this.msal.loginRedirect(loginRequest);
  }

  /** Cuentas institucionales (Duoc) inician sesión como invitadas del tenant propio. */
  loginInstitutional(): Promise<void> {
    return this.msal.loginRedirect(institutionalLoginRequest);
  }

  logout(): Promise<void> {
    return this.msal.logoutRedirect({ postLogoutRedirectUri: `${window.location.origin}/catalog` });
  }

  /** Devuelve (y borra) el último error de inicio de sesión, si lo hay. */
  consumeAuthError(): string | null {
    const error = sessionStorage.getItem(AUTH_ERROR_KEY);
    sessionStorage.removeItem(AUTH_ERROR_KEY);
    return error;
  }
}
