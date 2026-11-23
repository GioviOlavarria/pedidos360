import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from './auth.service';

/**
 * Guard de rutas por autenticación y rol de Microsoft Entra ID.
 *  - Sin sesión            → /login
 *  - Con sesión sin el rol → redirectTo (si se indica) o /acceso-denegado
 *  - Con el rol            → permite la navegación
 * Una lista de roles vacía significa "cualquier usuario autenticado".
 */
export function roleGuard(allowedRoles: readonly Role[], redirectTo?: string): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    if (!auth.hasAnyRole(allowedRoles)) {
      return router.createUrlTree([redirectTo ?? '/acceso-denegado']);
    }
    return true;
  };
}
