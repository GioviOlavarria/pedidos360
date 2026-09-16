import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { EventType } from '@azure/msal-browser';

import useUserRoles from '../hooks/useUserRoles';

/**
 * AuthCallbackPage
 * Azure AD hace redirect a /auth/callback luego del login.
 * MSAL procesa automáticamente el hash/query de la URL al inicializar.
 * Si es Admin va a /dashboard, si es Cliente va a /catalog.
 */
function AuthCallbackPage() {
  const { instance, accounts, inProgress } = useMsal();
  const navigate = useNavigate();
  const userRoles = useUserRoles();

  useEffect(() => {
    // Solo decidir y navegar cuando MSAL haya terminado de procesar todo.
    if (inProgress === 'none') {
      if (accounts.length > 0) {
        const dest = userRoles.includes('Admin') ? '/dashboard' : '/catalog';
        navigate(dest, { replace: true });
      } else {
        // Si terminó y no hay cuenta, algo falló o se canceló, volver al login
        navigate('/login', { replace: true });
      }
    }
  }, [inProgress, accounts, userRoles, navigate]);

  return (
    <div style={containerStyle}>
      <p style={{ color: '#374151', fontSize: '1rem' }}>
        Procesando autenticación...
      </p>
    </div>
  );
}

const containerStyle = {
  minHeight: '100vh',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

export default AuthCallbackPage;
