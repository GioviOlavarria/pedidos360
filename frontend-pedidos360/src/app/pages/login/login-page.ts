import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/** Inicio de sesión con Microsoft Entra ID (cuentas Microsoft o institucionales invitadas). */
@Component({
  selector: 'app-login-page',
  imports: [RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
})
export class LoginPage implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly authError = this.auth.consumeAuthError();
  protected readonly stats = [
    { value: '+500', label: 'Empresas activas' },
    { value: '99.9%', label: 'Disponibilidad' },
    { value: '< 1s', label: 'Tiempo de respuesta' },
  ];

  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      void this.router.navigateByUrl(this.auth.isAdmin() ? '/dashboard' : '/', { replaceUrl: true });
    }
  }

  protected login(): void {
    this.auth.login().catch(console.error);
  }

  protected loginInstitutional(): void {
    this.auth.loginInstitutional().catch(console.error);
  }
}
