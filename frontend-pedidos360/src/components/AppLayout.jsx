import { NavLink } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import useUserRoles from '../hooks/useUserRoles';
import LogoutButton from './LogoutButton';

const NAVY  = '#003087';
const ORANGE = '#FF6B00';

function AppLayout({ children }) {
  const { accounts } = useMsal();
  const userRoles = useUserRoles();

  const userName      = accounts[0]?.name ?? accounts[0]?.username ?? 'Usuario';
  const userInitial   = userName.charAt(0).toUpperCase();
  const canSeeCatalog = userRoles.some(r => r === 'Admin' || r === 'Operator');
  const isAdmin       = userRoles.some(r => r === 'Admin');

  return (
    <div style={{
      display: 'flex',
      minHeight: '100vh',
      width: '100vw',
      fontFamily: "'Segoe UI', system-ui, sans-serif",
    }}>

      {/* ── Sidebar ───────────────────────────────────────────────────── */}
      <aside style={{
        width: '240px',
        minWidth: '240px',
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
          <span style={{ color: '#fff', fontSize: '1.15rem', fontWeight: '800', letterSpacing: '-0.3px' }}>
            Pedidos<span style={{ color: ORANGE }}>360</span>
          </span>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1, padding: '20px 12px' }}>
          <p style={{
            fontSize: '0.65rem', fontWeight: '700', color: 'rgba(255,255,255,0.35)',
            textTransform: 'uppercase', letterSpacing: '0.1em',
            padding: '0 10px', marginBottom: '8px',
          }}>
            Menú
          </p>
          <ul style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: '2px' }}>
            <NavItem to="/dashboard" icon="🏠" label="Panel principal" end />
            <NavItem to="/orders"    icon="📦" label="Pedidos" />
            {canSeeCatalog && <NavItem to="/catalog" icon="🗂️" label="Catálogo" />}
            {isAdmin && <NavItem to="/reports" icon="📊" label="Reportes" />}
          </ul>
        </nav>

        {/* Usuario + logout */}
        <div style={{ padding: '16px 20px', borderTop: '1px solid rgba(255,255,255,0.1)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
            <div style={{
              width: '36px', height: '36px', borderRadius: '50%',
              background: ORANGE,
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
              {userRoles.length > 0 && (
                <div style={{
                  fontSize: '0.68rem', color: ORANGE, fontWeight: '700',
                  textTransform: 'uppercase', letterSpacing: '0.04em',
                }}>
                  {userRoles[0]}
                </div>
              )}
            </div>
          </div>
          <LogoutButton />
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
