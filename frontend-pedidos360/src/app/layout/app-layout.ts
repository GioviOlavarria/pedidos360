import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { CartService } from '../core/cart.service';

interface NavItem {
  path: string;
  icon: string;
  label: string;
  exact?: boolean;
  /** Muestra el número de productos del carrito. */
  cart?: boolean;
}

/** Estructura común: barra lateral con menú según el rol y sesión, y el contenido de la ruta. */
@Component({
  selector: 'app-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app-layout.html',
  styleUrl: './app-layout.css',
})
export class AppLayout {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartService);

  protected readonly initial = computed(() => this.auth.displayName().charAt(0).toUpperCase());

  protected readonly navItems = computed<NavItem[]>(() => {
    if (this.auth.isAdmin()) {
      return [
        { path: '/dashboard', icon: '🏠', label: 'Panel principal', exact: true },
        { path: '/catalog', icon: '🗂️', label: 'Catálogo e Inventario' },
        { path: '/orders', icon: '📦', label: 'Gestión de Pedidos' },
        { path: '/reports', icon: '📊', label: 'Reportes Financieros' },
      ];
    }
    return [
      { path: '/', icon: '🏠', label: 'Inicio', exact: true },
      { path: '/catalog', icon: '🛍️', label: 'Catálogo de Productos' },
      { path: '/carrito', icon: '🛒', label: 'Carrito', cart: true },
      ...(this.auth.isAuthenticated() ? [{ path: '/orders', icon: '📦', label: 'Mis Pedidos' }] : []),
      { path: '/tracking', icon: '🔍', label: 'Seguimiento de Envíos' },
    ];
  });

  protected logout(): void {
    this.auth.logout().catch(console.error);
  }
}
