import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';
import { AppLayout } from './layout/app-layout';

/**
 * Rutas y acceso por rol (Admin, Operator, Customer de Microsoft Entra ID):
 *   /, /inicio           → inicio público (catálogo; con sesión, también el seguimiento de sus pedidos)
 *   /tracking            → seguimiento de envíos (autenticado: Admin ve todos, Customer solo los suyos)
 *   /catalog             → catálogo público
 *   /login               → inicio de sesión con Microsoft
 *   /auth/callback       → retorno del login (MSAL)
 *   /dashboard           → Admin, Operator (el resto va a /catalog)
 *   /orders              → cualquier usuario autenticado
 *   /reports             → Admin
 *   /carrito             → carrito (público; pagar exige sesión)
 *   /checkout/*          → pasarela simulada y resultado del pago (autenticado)
 */
export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión · Pedidos360',
    loadComponent: () => import('./pages/login/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'auth/callback',
    loadComponent: () => import('./pages/auth-callback/auth-callback-page').then((m) => m.AuthCallbackPage),
  },
  {
    path: 'acceso-denegado',
    title: 'Acceso denegado · Pedidos360',
    loadComponent: () => import('./pages/access-denied/access-denied-page').then((m) => m.AccessDeniedPage),
  },
  {
    path: '',
    component: AppLayout,
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: 'Pedidos360',
        loadComponent: () => import('./pages/home/home-page').then((m) => m.HomePage),
      },
      {
        path: 'inicio',
        title: 'Pedidos360',
        loadComponent: () => import('./pages/home/home-page').then((m) => m.HomePage),
      },
      {
        path: 'tracking',
        title: 'Seguimiento · Pedidos360',
        canActivate: [roleGuard([])],
        loadComponent: () => import('./pages/home/home-page').then((m) => m.HomePage),
        data: { segment: 'tracking' },
      },
      {
        path: 'catalog',
        title: 'Catálogo · Pedidos360',
        loadComponent: () => import('./pages/catalog/catalog-page').then((m) => m.CatalogPage),
      },
      {
        path: 'carrito',
        title: 'Carrito · Pedidos360',
        loadComponent: () => import('./pages/cart/cart-page').then((m) => m.CartPage),
      },
      {
        path: 'checkout/simulado',
        title: 'Pago · Pedidos360',
        canActivate: [roleGuard([])],
        loadComponent: () =>
          import('./pages/checkout-simulated/checkout-simulated-page').then((m) => m.CheckoutSimulatedPage),
      },
      {
        path: 'checkout/resultado',
        title: 'Resultado del pago · Pedidos360',
        canActivate: [roleGuard([])],
        loadComponent: () => import('./pages/checkout-result/checkout-result-page').then((m) => m.CheckoutResultPage),
      },
      {
        path: 'dashboard',
        title: 'Panel · Pedidos360',
        canActivate: [roleGuard(['Admin', 'Operator'], '/catalog')],
        loadComponent: () => import('./pages/dashboard/dashboard-page').then((m) => m.DashboardPage),
      },
      {
        path: 'orders',
        title: 'Pedidos · Pedidos360',
        canActivate: [roleGuard(['Admin', 'Operator', 'Customer'])],
        loadComponent: () => import('./pages/orders/orders-page').then((m) => m.OrdersPage),
      },
      {
        path: 'reports',
        title: 'Reportes · Pedidos360',
        canActivate: [roleGuard(['Admin'])],
        loadComponent: () => import('./pages/reports/reports-page').then((m) => m.ReportsPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
