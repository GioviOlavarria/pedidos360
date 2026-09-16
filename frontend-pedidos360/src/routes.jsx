import { Routes, Route, Navigate } from 'react-router-dom';

import ProtectedRoute from './components/ProtectedRoute';
import AppLayout      from './components/AppLayout';
import LoginPage        from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import DashboardPage    from './pages/DashboardPage';
import OrdersPage       from './pages/OrdersPage';
import CatalogPage      from './pages/CatalogPage';
import ReportsPage      from './pages/ReportsPage';

/**
 * Roles exactos definidos en instruccionesProyecto.md y en Azure AD:
 *   Admin, Operator, Customer
 *
 * Reglas de acceso:
 *   /dashboard → cualquier rol autenticado            (allowedRoles=[])
 *   /orders    → Admin, Operator, Customer
 *   /catalog   → Admin, Operator
 *   /reports   → Admin
 */
function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login"         element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />

      <Route path="/dashboard" element={<ProtectedRoute allowedRoles={['Admin', 'Operator']} redirectTo="/catalog"><AppLayout><DashboardPage /></AppLayout></ProtectedRoute>} />
      <Route path="/orders" element={<ProtectedRoute allowedRoles={['Admin', 'Operator', 'Customer']}><AppLayout><OrdersPage /></AppLayout></ProtectedRoute>} />
      <Route path="/catalog" element={<ProtectedRoute allowedRoles={['Admin', 'Operator', 'Customer']}><AppLayout><CatalogPage /></AppLayout></ProtectedRoute>} />
      <Route path="/reports" element={<ProtectedRoute allowedRoles={['Admin']}><AppLayout><ReportsPage /></AppLayout></ProtectedRoute>} />
    </Routes>
  );
}

export default AppRoutes;
