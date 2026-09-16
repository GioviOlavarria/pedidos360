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
    // 1. Si ya tenemos cuenta y no hay nada en progreso, redirigimos
    if (inProgress === 'none') {
      if (accounts.length > 0) {
        const dest = userRoles.includes('Admin') ? '/dashboard' : '/catalog';
        navigate(dest, { replace: true });
      } else {
        navigate('/login', { replace: true });
      }
      return;
    }

    // 2. Si todavía está procesando el redirect, escuchamos el evento
    const callbackId = instance.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
        // En lugar de depender del estado (que puede estar desactualizado),
        // calculamos el rol directamente del payload del evento.
        const claims = event.payload.idTokenClaims || {};
        const username = (event.payload.account?.username || claims.preferred_username || claims.email || '').toLowerCase();
        const adminEmail = (import.meta.env.VITE_ADMIN_EMAIL || 'gi.olavarria@duocuc.cl').toLowerCase();
        
        const tokenRoles = claims.roles ? (Array.isArray(claims.roles) ? claims.roles : [claims.roles]) : [];
        const isAdmin = tokenRoles.includes('Admin') || (adminEmail && username === adminEmail);
        
        navigate(isAdmin ? '/dashboard' : '/catalog', { replace: true });
      }
      
      if (event.eventType === EventType.LOGIN_FAILURE) {
        navigate('/login', { replace: true });
      }
    });

    return () => {
      if (callbackId) instance.removeEventCallback(callbackId);
    };
  }, [instance, accounts, inProgress, userRoles, navigate]);

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
