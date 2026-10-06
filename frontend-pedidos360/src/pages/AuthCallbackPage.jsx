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
    // Si ya procesó y tenemos cuenta, navegamos a donde corresponda
    if (accounts.length > 0) {
      const account = accounts[0];
      const claims = account?.idTokenClaims || {};
      console.log('=== DEBUG LOGIN (cuenta ya cargada) ===');
      console.log('account.username:', account?.username);
      console.log('claims.preferred_username:', claims.preferred_username);
      console.log('claims.email:', claims.email);
      console.log('claims (completo):', JSON.stringify(claims));
      console.log('userRoles:', JSON.stringify(userRoles));
      console.log('=======================================');
      const dest = userRoles.includes('Admin') ? '/dashboard' : '/catalog';
      console.log('Navegando a:', dest);
      navigate(dest, { replace: true });
      return;
    }

    // Si no hay cuenta aún, esperamos el evento de MSAL en lugar de redirigir ciegamente
    const callbackId = instance.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
        const claims = event.payload.idTokenClaims || {};
        const accountUsername = (event.payload.account?.username || '').toLowerCase();
        const preferredUsername = (claims.preferred_username || '').toLowerCase();
        const emailClaim = (claims.email || '').toLowerCase();
        
        // DEBUG: Muestra en consola lo que devuelve Azure AD
        console.log('=== DEBUG LOGIN ===');
        console.log('account.username:', event.payload.account?.username);
        console.log('claims.preferred_username:', claims.preferred_username);
        console.log('claims.email:', claims.email);
        console.log('claims.upn:', claims.upn);
        console.log('claims.unique_name:', claims.unique_name);
        console.log('claims (completo):', JSON.stringify(claims));
        console.log('==================');
        
        const tokenRoles = claims.roles ? (Array.isArray(claims.roles) ? claims.roles : [claims.roles]) : [];
        
        const adminEmail = (import.meta.env.VITE_ADMIN_EMAIL || '').toLowerCase();
        const matchesAdmin = (str) => adminEmail && str.includes(adminEmail);
        const isAdmin = tokenRoles.includes('Admin') || 
                       matchesAdmin(accountUsername) || 
                       matchesAdmin(preferredUsername) || 
                       matchesAdmin(emailClaim);
        
        console.log('isAdmin resultado:', isAdmin);
        navigate(isAdmin ? '/dashboard' : '/catalog', { replace: true });
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
