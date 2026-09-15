import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { EventType } from '@azure/msal-browser';

/**
 * AuthCallbackPage
 * Azure AD hace redirect a /auth/callback luego del login.
 * MSAL procesa automáticamente el hash/query de la URL al inicializar.
 * Este componente espera el evento LOGIN_SUCCESS y navega a /dashboard.
 *
 * Si MSAL ya procesó el token antes de montar este componente
 * (accounts.length > 0), navega de inmediato.
 */
function AuthCallbackPage() {
  const { instance, accounts } = useMsal();
  const navigate = useNavigate();

  useEffect(() => {
    // Si ya hay cuenta activa (MSAL procesó el token), ir directo
    if (accounts.length > 0) {
      navigate('/dashboard', { replace: true });
      return;
    }

    // Escuchar el evento de login exitoso en caso de que todavía esté procesando
    const callbackId = instance.addEventCallback((event) => {
      if (event.eventType === EventType.LOGIN_SUCCESS && event.payload) {
        navigate('/dashboard', { replace: true });
      }
    });

    return () => {
      if (callbackId) instance.removeEventCallback(callbackId);
    };
  }, [instance, accounts, navigate]);

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
