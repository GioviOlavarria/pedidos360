import { Routes, Route } from 'react-router-dom';

import ProtectedRoute from './components/ProtectedRoute';
import AppLayout      from './components/AppLayout';
import LoginPage        from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import DashboardPage    from './pages/DashboardPage';
import OrdersPage       from './pages/OrdersPage';
import CatalogPage      from './pages/CatalogPage';
import ReportsPage      from './pages/ReportsPage';

import HomePage         from './pages/HomePage';

/**
 * Roles exactos definidos en instruccionesProyecto.md y en Azure AD:
 *   Admin, Operator, Customer
 *
 * Reglas de acceso:
 *   /              → Página de inicio pública (catálogo y seguimiento de pedidos)
 *   /catalog       → Catálogo público de productos
 *   /tracking      → Seguimiento público de envíos
 *   /login         → Autenticación con Microsoft Entra ID
 *   /auth/callback → Retorno de autenticación MSAL
 *   /dashboard     → Admin, Operator
 *   /orders        → Admin, Operator, Customer (requiere inicio de sesión)
 *   /reports       → Admin
 */
function AppRoutes() {
  return (
    <Routes>
      {/* Rutas públicas: accesibles sin iniciar sesión */}
      <Route path="/"              element={<AppLayout><HomePage /></AppLayout>} />
      <Route path="/inicio"        element={<AppLayout><HomePage /></AppLayout>} />
      <Route path="/catalog"       element={<AppLayout><CatalogPage /></AppLayout>} />
      <Route path="/tracking"      element={<AppLayout><HomePage /></AppLayout>} />
      <Route path="/login"         element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />

      {/* Rutas protegidas: requieren autenticación y rol específico */}
      <Route path="/dashboard" element={<ProtectedRoute allowedRoles={['Admin', 'Operator']} redirectTo="/catalog"><AppLayout><DashboardPage /></AppLayout></ProtectedRoute>} />
      <Route path="/orders"    element={<ProtectedRoute allowedRoles={['Admin', 'Operator', 'Customer']}><AppLayout><OrdersPage /></AppLayout></ProtectedRoute>} />
      <Route path="/reports"   element={<ProtectedRoute allowedRoles={['Admin']}><AppLayout><ReportsPage /></AppLayout></ProtectedRoute>} />
    </Routes>
  );
}

export default AppRoutes;
