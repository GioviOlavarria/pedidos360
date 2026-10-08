import { NavLink } from 'react-router-dom';
import { useMsal, useIsAuthenticated } from '@azure/msal-react';
import useUserRoles from '../hooks/useUserRoles';
import LogoutButton from './LogoutButton';
import LoginButton from './LoginButton';

const NAVY  = '#003087';
const ORANGE = '#FF6B00';

function AppLayout({ children }) {
  const { accounts, instance } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const userRoles = useUserRoles();

  const userName      = accounts[0]?.name ?? accounts[0]?.username ?? 'Invitado';
  const userInitial   = userName.charAt(0).toUpperCase();
  const isAdmin       = isAuthenticated && userRoles.includes('Admin');

  return (
    <div style={{
      display: 'flex',
      minHeight: '100vh',
      width: '100vw',
      fontFamily: "'Segoe UI', system-ui, sans-serif",
    }}>

      {/* ── Sidebar ───────────────────────────────────────────────────── */}
      <aside style={{
        width: '250px',
        minWidth: '250px',
        background: NAVY,
        display: 'flex',
        flexDirection: 'column',
        padding: '0',
      }}>
        {/* Logo */}
        <div style={{
          padding: '24px 20px',
          borderBottom: '1px solid rgba(255,255,255,0.1)',
          display: 'flex', alignItems: 'center', gap: '10px',
        }}>
          <div style={{
            width: '36px', height: '36px',
            background: ORANGE, borderRadius: '9px',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
              <line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
          </div>
          <div>
            <span style={{ color: '#fff', fontSize: '1.15rem', fontWeight: '800', letterSpacing: '-0.3px', display: 'block' }}>
              Pedidos<span style={{ color: ORANGE }}>360</span>
            </span>
            <span style={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.68rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              {isAuthenticated ? (isAdmin ? 'Panel Administrador' : 'Portal Cliente') : 'Catálogo Público'}
            </span>
          </div>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: '20px 12px' }}>
          <p style={{
            fontSize: '0.65rem', fontWeight: '700', color: 'rgba(255,255,255,0.35)',
            textTransform: 'uppercase', letterSpacing: '0.1em',
            padding: '0 10px', marginBottom: '8px',
          }}>
            Menú {isAdmin ? 'Administrativo' : 'Principal'}
          </p>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {isAdmin ? (
              <>
                <NavItem to="/dashboard" icon="🏠" label="Panel principal" end />
                <NavItem to="/catalog" icon="🗂️" label="Catálogo e Inventario" />
                <NavItem to="/orders" icon="📦" label="Gestión de Pedidos" />
                <NavItem to="/reports" icon="📊" label="Reportes Financieros" />
              </>
            ) : (
              <>
                <NavItem to="/" icon="🏠" label="Inicio" end />
                <NavItem to="/catalog" icon="🛍️" label="Catálogo de Productos" />
                {isAuthenticated && (
                  <NavItem to="/orders" icon="📦" label="Mis Pedidos" />
                )}
                <NavItem to="/tracking" icon="🔍" label="Seguimiento de Envíos" />
                {!isAuthenticated && (
                  <li style={{ marginTop: '8px' }}>
                    <button
                      type="button"
                      onClick={() => {
                        import('../authConfig').then(({ loginRequest }) => {
                          instance.loginRedirect(loginRequest).catch(() => window.location.assign('/login'));
                        });
                      }}
                      style={{
                        width: '100%',
                        display: 'flex', alignItems: 'center', gap: '10px',
                        padding: '10px 12px', borderRadius: '8px',
                        border: '1px solid rgba(255,107,0,0.4)',
                        background: 'rgba(255,107,0,0.15)',
                        color: '#fff',
                        fontWeight: '600',
                        fontSize: '0.88rem',
                        cursor: 'pointer',
                        textAlign: 'left',
                        transition: 'all 0.15s ease',
                      }}
                      onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,107,0,0.28)'; }}
                      onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,107,0,0.15)'; }}
                    >
                      <span style={{ fontSize: '1rem' }}>🔐</span>
                      Iniciar sesión
                    </button>
                  </li>
                )}
              </>
            )}
          </ul>
        </nav>

        {/* Usuario + login/logout en panel de opciones */}
        <div style={{ padding: '16px 20px', borderTop: '1px solid rgba(255,255,255,0.1)' }}>
          {isAuthenticated ? (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                <div style={{
                  width: '36px', height: '36px', borderRadius: '50%',
                  background: isAdmin ? ORANGE : '#10b981',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '0.9rem', fontWeight: '800', color: '#fff', flexShrink: 0,
                }}>
                  {userInitial}
                </div>
                <div style={{ overflow: 'hidden', flex: 1 }}>
                  <div style={{
                    fontSize: '0.82rem', fontWeight: '600', color: '#fff',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                  }}>
                    {userName}
                  </div>
                  <div style={{
                    fontSize: '0.68rem', 
                    color: isAdmin ? ORANGE : '#34d399', 
                    fontWeight: '700',
                    textTransform: 'uppercase', 
                    letterSpacing: '0.04em',
                  }}>
                    {isAdmin ? 'ADMINISTRADOR' : 'CLIENTE'}
                  </div>
                </div>
              </div>
              <LogoutButton />
            </>
          ) : (
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
                <div style={{
                  width: '36px', height: '36px', borderRadius: '50%',
                  background: 'rgba(255,255,255,0.12)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '1rem', color: '#fff', flexShrink: 0,
                }}>
                  👤
                </div>
                <div style={{ overflow: 'hidden', flex: 1 }}>
                  <div style={{
                    fontSize: '0.82rem', fontWeight: '600', color: '#fff',
                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                  }}>
                    Modo Invitado
                  </div>
                  <div style={{
                    fontSize: '0.68rem', 
                    color: 'rgba(255,255,255,0.45)', 
                    fontWeight: '500',
                  }}>
                    Sin sesión activa
                  </div>
                </div>
              </div>
              <LoginButton />
            </div>
          )}
        </div>
      </aside>

      {/* ── Contenido ─────────────────────────────────────────────────── */}
      <main style={{ flex: 1, background: '#f0f4f8', overflow: 'auto', minWidth: 0 }}>
        {children}
      </main>
    </div>
  );
}

function NavItem({ to, icon, label, end }) {
  return (
    <li>
      <NavLink
        to={to}
        end={end}
        style={({ isActive }) => ({
          display: 'flex', alignItems: 'center', gap: '10px',
          padding: '10px 12px', borderRadius: '8px',
          textDecoration: 'none',
          color: isActive ? '#fff' : 'rgba(255,255,255,0.55)',
          background: isActive ? 'rgba(255,107,0,0.25)' : 'transparent',
          borderLeft: isActive ? `3px solid ${ORANGE}` : '3px solid transparent',
          fontWeight: isActive ? '600' : '400',
          fontSize: '0.88rem',
          transition: 'all 0.15s',
        })}
        onMouseEnter={e => { if (!e.currentTarget.dataset.active) e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; }}
        onMouseLeave={e => { if (!e.currentTarget.dataset.active) e.currentTarget.style.background = 'transparent'; }}
      >
        <span style={{ fontSize: '1rem' }}>{icon}</span>
        {label}
      </NavLink>
    </li>
  );
}

export default AppLayout;
