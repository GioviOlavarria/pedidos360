import { useState, useEffect } from 'react';
import { getOrders } from '../services/ordersService';

const NAVY = '#003087';

const STATUS_CFG = {
  CREADO:         { bg: '#dbeafe', color: '#1d4ed8', label: 'Creado' },
  ACEPTADO:       { bg: '#ede9fe', color: '#6d28d9', label: 'Aceptado' },
  EN_PREPARACION: { bg: '#fef9c3', color: '#92400e', label: 'En prep.' },
  DESPACHADO:     { bg: '#ffedd5', color: '#c2410c', label: 'Despachado' },
  ENTREGADO:      { bg: '#dcfce7', color: '#15803d', label: 'Entregado' },
  CANCELADO:      { bg: '#fee2e2', color: '#b91c1c', label: 'Cancelado' },
};

function StatusPill({ status }) {
  const s = STATUS_CFG[status] ?? { bg: '#f1f5f9', color: '#475569', label: status };
  return (
    <span style={{
      background: s.bg, color: s.color,
      padding: '4px 10px', borderRadius: '20px',
      fontSize: '0.7rem', fontWeight: '700', textTransform: 'uppercase', letterSpacing: '0.04em'
    }}>
      {s.label}
    </span>
  );
}

function OrderList({ onSelect }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getOrders()
      .then(res => setOrders(res.data))
      .catch(() => setError('Error al cargar pedidos'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p style={{ color: '#94a3b8' }}>Cargando pedidos...</p>;
  if (error) return <p style={{ color: '#ef4444' }}>{error}</p>;
  if (orders.length === 0) return <p style={{ color: '#94a3b8' }}>No hay pedidos registrados.</p>;

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #f1f5f9' }}>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>ID</th>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Cliente</th>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Total</th>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Estado</th>
            <th style={{ padding: '10px', textAlign: 'center', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Acción</th>
          </tr>
        </thead>
        <tbody>
          {orders.map(o => (
            <tr key={o.id} style={{ borderBottom: '1px solid #f8fafc', transition: 'background 0.15s' }}>
              <td style={{ padding: '12px 10px', fontWeight: '600', color: '#64748b' }}>#{o.id}</td>
              <td style={{ padding: '12px 10px', color: NAVY, fontWeight: '600' }}>Cliente {o.customerId}</td>
              <td style={{ padding: '12px 10px', fontWeight: '700', color: NAVY }}>${o.total}</td>
              <td style={{ padding: '12px 10px' }}><StatusPill status={o.status} /></td>
              <td style={{ padding: '12px 10px', textAlign: 'center' }}>
                <button
                  onClick={() => onSelect(o)}
                  style={{
                    background: '#f1f5f9', color: NAVY, border: 'none', borderRadius: '6px',
                    padding: '6px 12px', fontSize: '0.75rem', fontWeight: '600', cursor: 'pointer',
                    transition: 'background 0.15s'
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = '#e2e8f0'}
                  onMouseLeave={e => e.currentTarget.style.background = '#f1f5f9'}
                >
                  Ver
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default OrderList;
