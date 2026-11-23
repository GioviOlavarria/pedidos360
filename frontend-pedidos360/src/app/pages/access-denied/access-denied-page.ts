import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Usuario autenticado sin el rol que exige la ruta (403). */
@Component({
  selector: 'app-access-denied-page',
  imports: [RouterLink],
  template: `
    <div class="denied">
      <div class="card">
        <span class="icon">🚫</span>
        <h2>Acceso denegado</h2>
        <p>No tiene los permisos necesarios para ver esta página.</p>
        <a routerLink="/" class="p-btn p-btn-navy">Volver al inicio</a>
      </div>
    </div>
  `,
  styles: `
    .denied {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f3f4f6;
    }
    .card {
      text-align: center;
      background: #fff;
      border-radius: 12px;
      padding: 48px 56px;
      box-shadow: 0 4px 24px rgba(0, 0, 0, 0.09);
    }
    .icon {
      font-size: 3rem;
    }
    h2 {
      margin: 12px 0 8px;
    }
    p {
      color: #6b7280;
      margin: 0 0 24px;
    }
  `,
})
export class AccessDeniedPage {}
