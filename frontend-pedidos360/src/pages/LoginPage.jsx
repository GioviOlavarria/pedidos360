import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal, useIsAuthenticated } from '@azure/msal-react';
import { loginRequest } from '../authConfig';

/* ── Paleta Blue Express-inspired ───────────────────────────────────────── */
const NAVY  = '#003087';
const ORANGE = '#FF6B00';

function LoginPage() {
  const { instance } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) navigate('/dashboard', { replace: true });
  }, [isAuthenticated, navigate]);

  function handleLogin() {
    instance.loginRedirect(loginRequest).catch(console.error);
  }

  return (
    <div style={{
      display: 'flex',
      minHeight: '100vh',
      width: '100vw',
      margin: 0,
      padding: 0,
      fontFamily: "'Segoe UI', system-ui, sans-serif",
      overflow: 'hidden',
    }}>

      {/* ── Panel izquierdo — branding ─────────────────────────────────── */}
      <div style={{
        flex: 1,
        background: `linear-gradient(160deg, ${NAVY} 60%, #001a4d 100%)`,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'center',
        padding: '60px 48px',
        position: 'relative',
        overflow: 'hidden',
      }}>
        {/* Círculo decorativo fondo */}
        <div style={{
          position: 'absolute', bottom: '-120px', left: '-120px',
          width: '400px', height: '400px',
          borderRadius: '50%',
          background: 'rgba(255,107,0,0.08)',
          pointerEvents: 'none',
        }} />
        <div style={{
          position: 'absolute', top: '-80px', right: '-80px',
          width: '280px', height: '280px',
          borderRadius: '50%',
          background: 'rgba(255,255,255,0.04)',
          pointerEvents: 'none',
        }} />

        {/* Logo + nombre */}
        <div style={{ textAlign: 'center', marginBottom: '40px', zIndex: 1 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: '72px', height: '72px',
            background: ORANGE,
            borderRadius: '18px',
            marginBottom: '20px',
            boxShadow: '0 8px 32px rgba(255,107,0,0.4)',
          }}>
            {/* Ícono de caja / paquete */}
            <svg width="38" height="38" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
          </div>
          <h1 style={{ color: '#fff', fontSize: '2.2rem', fontWeight: '800', margin: '0 0 8px', letterSpacing: '-0.5px' }}>
            Pedidos<span style={{ color: ORANGE }}>360</span>
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.6)', fontSize: '1rem', margin: 0 }}>
            Plataforma de gestión comercial
          </p>
        </div>

        {/* Tagline */}
        <div style={{ textAlign: 'center', marginBottom: '48px', zIndex: 1 }}>
          <h2 style={{ color: '#fff', fontSize: '1.6rem', fontWeight: '700', lineHeight: 1.3, marginBottom: '14px' }}>
            Gestiona tus pedidos<br />con total control
          </h2>
          <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '0.95rem', lineHeight: 1.6, maxWidth: '320px' }}>
            Catálogo, seguimiento en tiempo real y<br />gestión de equipos en un solo lugar.
          </p>
        </div>

        {/* Stats */}
        <div style={{
          display: 'flex', gap: '32px', zIndex: 1,
          borderTop: '1px solid rgba(255,255,255,0.12)',
          paddingTop: '32px',
        }}>
          {[
            { value: '+500',  label: 'Empresas activas' },
            { value: '99.9%', label: 'Disponibilidad' },
            { value: '< 1s',  label: 'Tiempo de respuesta' },
          ].map(({ value, label }) => (
            <div key={label} style={{ textAlign: 'center' }}>
              <div style={{ color: ORANGE, fontSize: '1.5rem', fontWeight: '800', lineHeight: 1 }}>{value}</div>
              <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.72rem', marginTop: '4px', textTransform: 'uppercase', letterSpacing: '0.06em' }}>{label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* ── Panel derecho — login ──────────────────────────────────────── */}
      <div style={{
        width: '440px',
        minWidth: '440px',
        background: '#fff',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '60px 48px',
        boxShadow: '-8px 0 40px rgba(0,0,0,0.12)',
      }}>
        {/* Header */}
        <div style={{ marginBottom: '36px' }}>
          <h2 style={{ fontSize: '1.8rem', fontWeight: '800', color: NAVY, marginBottom: '8px' }}>
            Bienvenido
          </h2>
          <p style={{ color: '#64748b', fontSize: '0.93rem', margin: 0 }}>
            Ingrese con su cuenta corporativa de Microsoft para continuar.
          </p>
        </div>

        {/* Divider */}
        <div style={{ borderTop: '1px solid #f1f5f9', marginBottom: '28px' }} />

        {/* Botón Microsoft */}
        <button
          onClick={handleLogin}
          style={{
            width: '100%',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '12px',
            padding: '14px 20px',
            background: NAVY,
            color: '#fff',
            border: 'none',
            borderRadius: '10px',
            fontWeight: '700',
            fontSize: '0.95rem',
            cursor: 'pointer',
            transition: 'background 0.2s, transform 0.1s',
            boxShadow: `0 4px 16px rgba(0,48,135,0.3)`,
          }}
          onMouseEnter={e => { e.currentTarget.style.background = '#002070'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
          onMouseLeave={e => { e.currentTarget.style.background = NAVY;      e.currentTarget.style.transform = 'translateY(0)'; }}
          onMouseDown={e => e.currentTarget.style.transform = 'translateY(0)'}
        >
          {/* Logo Microsoft */}
          <svg width="22" height="22" viewBox="0 0 21 21" fill="none">
            <rect x="1"  y="1"  width="9" height="9" fill="#f25022"/>
            <rect x="11" y="1"  width="9" height="9" fill="#7fba00"/>
            <rect x="1"  y="11" width="9" height="9" fill="#00a4ef"/>
            <rect x="11" y="11" width="9" height="9" fill="#ffb900"/>
          </svg>
          Iniciar sesión con Microsoft
        </button>

        {/* Info acceso */}
        <div style={{
          marginTop: '28px',
          background: '#f8fafc',
          borderRadius: '10px',
          padding: '16px 18px',
          borderLeft: `4px solid ${ORANGE}`,
        }}>
          <p style={{ margin: 0, fontSize: '0.82rem', color: '#475569', lineHeight: 1.6 }}>
            <strong style={{ color: NAVY }}>Solo para usuarios autorizados.</strong><br />
            Si no puede acceder, contacte al administrador de su organización.
          </p>
        </div>

        {/* Footer */}
        <p style={{ marginTop: 'auto', paddingTop: '40px', fontSize: '0.75rem', color: '#cbd5e1', textAlign: 'center' }}>
          © 2026 Pedidos360 · Todos los derechos reservados
        </p>
      </div>
    </div>
  );
}

export default LoginPage;
