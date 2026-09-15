import { useMsal } from '@azure/msal-react';

function LogoutButton() {
  const { instance } = useMsal();

  function handleLogout() {
    instance.logoutRedirect({ postLogoutRedirectUri: 'http://localhost:3000/login' })
      .catch(console.error);
  }

  return (
    <button
      onClick={handleLogout}
      className="btn w-100"
      style={{
        background: 'rgba(255,255,255,0.07)',
        color: '#94a3b8',
        border: '1px solid rgba(255,255,255,0.1)',
        borderRadius: '8px',
        padding: '7px 12px',
        fontSize: '0.82rem',
        fontWeight: '500',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '6px',
        transition: 'all 0.15s',
      }}
      onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.13)'; e.currentTarget.style.color = '#e2e8f0'; }}
      onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.07)'; e.currentTarget.style.color = '#94a3b8'; }}
    >
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/>
        <polyline points="16 17 21 12 16 7"/>
        <line x1="21" y1="12" x2="9" y2="12"/>
      </svg>
      Cerrar sesión
    </button>
  );
}

export default LogoutButton;
