import { useMsal } from '@azure/msal-react';
import { useNavigate } from 'react-router-dom';
import { loginRequest } from '../authConfig';

function LoginButton() {
  const { instance } = useMsal();
  const navigate = useNavigate();

  function handleLogin() {
    instance.loginRedirect(loginRequest).catch((error) => {
      console.error('Error al redirigir al login:', error);
      navigate('/login');
    });
  }

  return (
    <button
      type="button"
      onClick={handleLogin}
      className="btn w-100"
      style={{
        background: '#FF6B00',
        color: '#ffffff',
        border: 'none',
        borderRadius: '8px',
        padding: '9px 12px',
        fontSize: '0.84rem',
        fontWeight: '700',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '8px',
        cursor: 'pointer',
        boxShadow: '0 3px 10px rgba(255,107,0,0.3)',
        transition: 'all 0.15s ease',
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = '#e66000';
        e.currentTarget.style.transform = 'translateY(-1px)';
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = '#FF6B00';
        e.currentTarget.style.transform = 'translateY(0)';
      }}
    >
      <svg width="15" height="15" viewBox="0 0 21 21" fill="none" style={{ flexShrink: 0 }}>
        <rect x="1" y="1" width="9" height="9" fill="#f25022" />
        <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
        <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
        <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
      </svg>
      Iniciar sesión
    </button>
  );
}

export default LoginButton;
