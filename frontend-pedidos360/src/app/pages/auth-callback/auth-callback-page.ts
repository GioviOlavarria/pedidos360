import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Microsoft Entra ID vuelve a /auth/callback tras el login. AuthService ya procesó la respuesta al
 * arrancar la app: el Admin va a /dashboard, un cliente a / y, si el login falló, se vuelve a /login,
 * donde se muestra el error devuelto por Microsoft.
 */
@Component({
  selector: 'app-auth-callback-page',
  template: '<div class="callback"><p>Procesando autenticación...</p></div>',
  styles: `
    .callback {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #374151;
    }
  `,
})
export class AuthCallbackPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.auth.refresh();
    const destination = !this.auth.isAuthenticated() ? '/login' : this.auth.isAdmin() ? '/dashboard' : '/';
    void this.router.navigateByUrl(destination, { replaceUrl: true });
  }
}
