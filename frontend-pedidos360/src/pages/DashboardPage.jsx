import { useMsal } from '@azure/msal-react';
import useUserRoles from '../hooks/useUserRoles';
import { useState, useEffect } from 'react';
import { getOrders } from '../services/ordersService';
import { formatMoney } from '../utils/userUtils';

const NAVY  = '#003087';
const ORANGE = '#FF6B00';

const STATUS_CFG = {
  CREADO:         { bg: '#dbeafe', color: '#1d4ed8', label: 'Creado' },
  ACEPTADO:       { bg: '#ede9fe', color: '#6d28d9', label: 'Aceptado' },
  EN_PREPARACION: { bg: '#fef9c3', color: '#92400e', label: 'En preparación' },
  DESPACHADO:     { bg: '#ffedd5', color: '#c2410c', label: 'Despachado' },
  ENTREGADO:      { bg: '#dcfce7', color: '#15803d', label: 'Entregado' },
  CANCELADO:      { bg: '#fee2e2', color: '#b91c1c', label: 'Cancelado' },
};

function StatusPill({ status }) {
  const s = STATUS_CFG[status] ?? { bg: '#f1f5f9', color: '#475569', label: status };
  return (
    <span style={{
      background: s.bg, color: s.color,
      padding: '3px 10px', borderRadius: '20px',
      fontSize: '0.73rem', fontWeight: '700',
      textTransform: 'uppercase', letterSpacing: '0.04em',
    }}>
      {s.label}
    </span>
  );
}

function DashboardPage() {
  const { accounts } = useMsal();
  const userRoles = useUserRoles();
  const userName = accounts[0]?.name?.split(' ')[0] ?? 'usuario';

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getOrders()
      .then(res => setOrders(res.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  // Calcular métricas reales
  const now = new Date();
  const thisMonthOrders = orders.filter(o => {
    const d = new Date(o.createdAt);
    return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
  });

  const totalThisMonth = thisMonthOrders.length;
  const ingresosThisMonth = thisMonthOrders.reduce((sum, o) => sum + (o.total || 0), 0);
  
  const entregados = orders.filter(o => o.status === 'ENTREGADO').length;
  const cancelados = orders.filter(o => o.status === 'CANCELADO').length;
  const enTransito = orders.filter(o => o.status === 'DESPACHADO').length;
  const enPrep = orders.filter(o => o.status === 'EN_PREPARACION').length;
  
  const totalActivos = orders.length > 0 ? orders.length : 1; // evitar division por 0
  const tasaEntrega = ((entregados / totalActivos) * 100).toFixed(1);

  const STATS = [
    { icon: '📦', value: totalThisMonth.toString(), label: 'Pedidos este mes',   delta: 'N/A',  up: true  },
    { icon: '✅', value: `${tasaEntrega}%`, label: 'Tasa de entrega',    delta: 'N/A', up: true  },
    { icon: '🕒', value: '18 min', label: 'Tiempo promedio',   delta: 'N/A', up: true  },
    { icon: '💰', value: `$${ingresosThisMonth.toLocaleString(undefined, { minimumFractionDigits: 2 })}`, label: 'Ingresos del mes', delta: 'N/A',  up: true  },
  ];

  // Ordenar por fecha descendente
  const recentOrders = [...orders].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 5);

  const pct = (val) => Math.round((val / totalActivos) * 100);

  return (
    <div style={{ padding: '32px 36px', minHeight: '100%' }}>

      {/* ── Header ────────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: '28px' }}>
        <div>
          <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: '0 0 4px' }}>
            Bienvenido, {userName} 👋
          </h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>
            {new Date().toLocaleDateString('es', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </div>
        {userRoles.length > 0 && (
          <span style={{
            background: NAVY, color: '#fff',
            padding: '6px 14px', borderRadius: '20px',
            fontSize: '0.75rem', fontWeight: '700',
            textTransform: 'uppercase', letterSpacing: '0.06em',
          }}>
            {userRoles[0]}
          </span>
        )}
      </div>

      {/* ── KPI Cards ─────────────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginBottom: '24px' }}>
        {STATS.map(({ icon, value, label, delta, up }) => (
          <div key={label} style={{
            background: '#fff', borderRadius: '14px',
            padding: '22px 24px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.07)',
            borderTop: `3px solid ${ORANGE}`,
          }}>
            <div style={{ fontSize: '1.4rem', marginBottom: '10px' }}>{icon}</div>
            <div style={{ fontSize: '1.8rem', fontWeight: '800', color: NAVY, lineHeight: 1 }}>{value}</div>
            <div style={{ fontSize: '0.78rem', color: '#94a3b8', margin: '4px 0 8px' }}>{label}</div>
            <div style={{ fontSize: '0.75rem', fontWeight: '700', color: up ? '#15803d' : '#b91c1c' }}>
              --
            </div>
          </div>
        ))}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: '20px' }}>

        {/* ── Tabla pedidos recientes ────────────────────────────────── */}
        <div style={{
          background: '#fff', borderRadius: '14px',
          padding: '24px', boxShadow: '0 1px 3px rgba(0,0,0,0.07)',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '18px' }}>
            <h2 style={{ fontSize: '1rem', fontWeight: '700', color: NAVY, margin: 0 }}>
              Pedidos recientes
            </h2>
            <a href="/orders" style={{ fontSize: '0.82rem', color: ORANGE, textDecoration: 'none', fontWeight: '700' }}>
              Ver todos →
            </a>
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.86rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #f1f5f9' }}>
                {['#', 'Cliente', 'Estado', 'Total', 'Fecha'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '8px 10px', color: '#94a3b8', fontWeight: '600', fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {recentOrders.length === 0 ? (
                <tr><td colSpan="5" style={{ textAlign: 'center', padding: '20px' }}>No hay pedidos registrados</td></tr>
              ) : recentOrders.map((o, i) => (
                <tr key={o.id} style={{ borderBottom: i < recentOrders.length - 1 ? '1px solid #f8fafc' : 'none' }}>
                  <td style={{ padding: '12px 10px', color: '#94a3b8', fontWeight: '600' }}>#{o.id}</td>
                  <td style={{ padding: '12px 10px', color: NAVY, fontWeight: '600' }}>Cliente {o.customerId}</td>
                  <td style={{ padding: '12px 10px' }}><StatusPill status={o.status} /></td>
                  <td style={{ padding: '12px 10px', fontWeight: '700', color: NAVY }}>{formatMoney(o.total)}</td>
                  <td style={{ padding: '12px 10px', color: '#94a3b8' }}>{new Date(o.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* ── Panel lateral ─────────────────────────────────────────── */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>

          {/* Distribución */}
          <div style={{
            background: '#fff', borderRadius: '14px',
            padding: '22px', boxShadow: '0 1px 3px rgba(0,0,0,0.07)',
          }}>
            <h2 style={{ fontSize: '0.95rem', fontWeight: '700', color: NAVY, marginBottom: '16px' }}>
              Distribución de pedidos
            </h2>
            {[
              { label: 'Entregados',     pct: pct(entregados), color: '#15803d' },
              { label: 'En tránsito',    pct: pct(enTransito), color: ORANGE },
              { label: 'En preparación', pct: pct(enPrep),  color: '#854d0e' },
              { label: 'Cancelados',     pct: pct(cancelados),  color: '#b91c1c' },
            ].map(({ label, pct, color }) => (
              <div key={label} style={{ marginBottom: '12px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '5px' }}>
                  <span style={{ color: '#475569' }}>{label}</span>
                  <span style={{ fontWeight: '700', color }}>{pct}%</span>
                </div>
                <div style={{ background: '#f1f5f9', borderRadius: '8px', height: '7px' }}>
                  <div style={{ width: `${pct}%`, background: color, borderRadius: '8px', height: '7px', transition: 'width 0.8s ease' }} />
                </div>
              </div>
            ))}
          </div>

          {/* Accesos rápidos */}
          <div style={{
            background: NAVY, borderRadius: '14px',
            padding: '22px',
          }}>
            <h2 style={{ fontSize: '0.95rem', fontWeight: '700', color: '#fff', marginBottom: '14px' }}>
              Accesos rápidos
            </h2>
            {[
              { href: '/orders',  icon: '📦', label: 'Gestionar pedidos',   sub: 'Ver y actualizar pedidos' },
              { href: '/catalog', icon: '🗂️', label: 'Ver catálogo',        sub: 'Productos y stock' },
            ].map(({ href, icon, label, sub }) => (
              <a key={href} href={href} style={{ textDecoration: 'none' }}>
                <div style={{
                  background: 'rgba(255,255,255,0.08)',
                  borderRadius: '10px', padding: '12px 14px',
                  marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '12px',
                  transition: 'background 0.15s', cursor: 'pointer',
                  borderLeft: `3px solid ${ORANGE}`,
                }}
                  onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.15)'}
                  onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.08)'}
                >
                  <span style={{ fontSize: '1.3rem' }}>{icon}</span>
                  <div>
                    <div style={{ color: '#fff', fontSize: '0.85rem', fontWeight: '600' }}>{label}</div>
                    <div style={{ color: 'rgba(255,255,255,0.45)', fontSize: '0.73rem' }}>{sub}</div>
                  </div>
                </div>
              </a>
            ))}
          </div>

        </div>
      </div>
    </div>
  );
}

export default DashboardPage;
