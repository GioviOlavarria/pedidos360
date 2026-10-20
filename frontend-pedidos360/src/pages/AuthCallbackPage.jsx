import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';

import { resolveRoles } from '../hooks/useUserRoles';

/**
 * AuthCallbackPage
 * Azure AD hace redirect a /auth/callback luego del login y MsalProvider procesa la respuesta.
 * Cuando MSAL termina: Admin va a /dashboard, Cliente va a /, y si el login falló se vuelve
 * a /login, donde se muestra el error devuelto por Microsoft.
 */
function AuthCallbackPage() {
  const { instance, accounts, inProgress } = useMsal();
  const navigate = useNavigate();

  useEffect(() => {
    if (inProgress !== InteractionStatus.None) return;

    const account = instance.getActiveAccount() || accounts[0];
    if (!account) {
      navigate('/login', { replace: true });
      return;
    }
    const isAdmin = resolveRoles(account).includes('Admin');
    navigate(isAdmin ? '/dashboard' : '/', { replace: true });
  }, [instance, accounts, inProgress, navigate]);

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
