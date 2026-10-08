import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { EventType } from '@azure/msal-browser';

import useUserRoles, { resolveRoles } from '../hooks/useUserRoles';

/**
 * AuthCallbackPage
 * Azure AD hace redirect a /auth/callback luego del login.
 * MSAL procesa automáticamente el hash/query de la URL al inicializar.
 * Si es Admin va a /dashboard, si es Cliente va a /catalog o /inicio.
 */
function AuthCallbackPage() {
  const { instance, accounts } = useMsal();
  const navigate = useNavigate();
  const userRoles = useUserRoles();

  useEffect(() => {
    // Si ya procesó y tenemos cuenta, navegamos a donde corresponda
    if (accounts.length > 0) {
      const account = accounts[0];
      const detectedRoles = resolveRoles(account);
      console.log('=== DEBUG LOGIN (cuenta ya cargada) ===');
      console.log('account:', account.username);
      console.log('detectedRoles:', detectedRoles);
      console.log('=======================================');
      const dest = detectedRoles.includes('Admin') ? '/dashboard' : '/';
      console.log('Navegando a:', dest);
      navigate(dest, { replace: true });
      return;
    }

    // Si no hay cuenta aún, esperamos el evento de MSAL en lugar de redirigir ciegamente
    const callbackId = instance.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
        const detectedRoles = resolveRoles(event.payload.account);
        console.log('=== DEBUG LOGIN (LOGIN_SUCCESS) ===');
        console.log('account:', event.payload.account?.username);
        console.log('detectedRoles:', detectedRoles);
        console.log('==================');

        const isAdmin = detectedRoles.includes('Admin');
        navigate(isAdmin ? '/dashboard' : '/', { replace: true });
      }
      
      if (event.eventType === EventType.LOGIN_FAILURE) {
        navigate('/login', { replace: true });
      }
    });

    return () => {
      if (callbackId) instance.removeEventCallback(callbackId);
    };
  }, [instance, accounts, userRoles, navigate]);

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
