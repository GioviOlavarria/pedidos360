import { Navigate } from 'react-router-dom';
import { useIsAuthenticated } from '@azure/msal-react';
import useUserRoles from '../hooks/useUserRoles';

/**
 * Pantalla de acceso denegado (403).
 * Componente inline: no necesita página propia porque no es una ruta navegable.
 */
function AccessDenied() {
  return (
    <div style={deniedContainerStyle}>
      <div style={deniedCardStyle}>
        <span style={{ fontSize: '3rem' }}>🚫</span>
        <h2 style={{ margin: '12px 0 8px' }}>Acceso denegado</h2>
        <p style={{ color: '#6b7280', margin: 0 }}>
          No tiene los permisos necesarios para ver esta página.
        </p>
      </div>
    </div>
  );
}

/**
 * ProtectedRoute
 * Guarda de rutas basada en autenticación y roles de Azure AD.
 *
 * Comportamiento:
 *  - No autenticado              → redirige a /login (replace para no romper el historial)
 *  - Autenticado sin rol requerido → muestra <AccessDenied>
 *  - Autenticado con rol requerido → renderiza {children}
 *
 * @param {{
 *   children: React.ReactNode,
 *   allowedRoles: string[]   // roles exactos del contrato: Admin, Operator, Customer
 * }} props
 *
 * Si allowedRoles está vacío ([]) se interpreta como "cualquier usuario autenticado".
 */
function ProtectedRoute({ children, allowedRoles = [] }) {
  const isAuthenticated = useIsAuthenticated();
  const userRoles       = useUserRoles();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  // allowedRoles vacío → sólo requiere estar autenticado
  if (allowedRoles.length > 0) {
    const hasRole = userRoles.some((role) => allowedRoles.includes(role));
    if (!hasRole) return <AccessDenied />;
  }

  return children;
}

// ── Estilos ──────────────────────────────────────────────────────────────────

const deniedContainerStyle = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: '#f3f4f6',
};

const deniedCardStyle = {
  textAlign: 'center',
  background: '#fff',
  borderRadius: '12px',
  padding: '48px 56px',
  boxShadow: '0 4px 24px rgba(0,0,0,0.09)',
};

export default ProtectedRoute;
