import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { InteractionRequiredAuthError } from '@azure/msal-browser';
import { EMPTY, catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { apiTokenRequest, msalInstance } from './msal.config';

/**
 * Adjunta el JWT de Microsoft Entra ID (Authorization: Bearer) a cada llamada al BFF.
 *  1. acquireTokenSilent: devuelve el token desde la caché o lo renueva sin interacción.
 *  2. Si el token expiró o fue revocado (InteractionRequiredAuthError) redirige al login de Microsoft.
 *  3. Sin sesión la petición sigue sin token (catálogo y seguimiento son públicos).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const account = inject(AuthService).currentAccount();
  if (!account || !req.url.startsWith(environment.bffUrl)) {
    return next(req);
  }

  const request = apiTokenRequest(account);
  return from(msalInstance.acquireTokenSilent(request)).pipe(
    catchError((error) => {
      if (error instanceof InteractionRequiredAuthError) {
        // acquireTokenRedirect navega fuera de la app: la petición actual no continúa.
        void msalInstance.acquireTokenRedirect(request);
        return EMPTY;
      }
      return throwError(() => error);
    }),
    switchMap((result) =>
      next(req.clone({ setHeaders: { Authorization: `Bearer ${result.accessToken}` } })),
    ),
  );
};
