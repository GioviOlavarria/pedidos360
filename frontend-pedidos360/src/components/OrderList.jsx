import { useState, useEffect } from 'react';
import { getOrders } from '../services/ordersService';
import { formatMoney } from '../utils/userUtils';

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

function OrderList({ onSelect, filterCustomerId }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    getOrders()
      .then(res => {
        let all = Array.isArray(res.data) ? res.data : [];
        if (filterCustomerId) {
          all = all.filter(o => o.customerId === filterCustomerId);
        }
        // Ordenar del más reciente al más antiguo
        all.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
        setOrders(all);
        if (all.length > 0 && onSelect) {
          onSelect(all[0]);
        }
      })
      .catch(() => setError('Error al cargar pedidos del servidor.'))
      .finally(() => setLoading(false));
  }, [filterCustomerId, onSelect]);

  if (loading) return <p style={{ color: '#94a3b8' }}>⏳ Cargando pedidos...</p>;
  if (error) return <p style={{ color: '#ef4444' }}>⚠️ {error}</p>;
  if (orders.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: '36px 16px', color: '#94a3b8' }}>
        <p style={{ margin: 0, fontWeight: '500' }}>
          {filterCustomerId 
            ? 'No has realizado pedidos todavía. ¡Explora el catálogo y haz tu primera compra!' 
            : 'No hay pedidos registrados en el sistema.'}
        </p>
      </div>
    );
  }

  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #f1f5f9' }}>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>ID</th>
            {!filterCustomerId && (
              <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Cliente</th>
            )}
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Total</th>
            <th style={{ padding: '10px', textAlign: 'left', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Estado</th>
            <th style={{ padding: '10px', textAlign: 'center', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Acción</th>
          </tr>
        </thead>
        <tbody>
          {orders.map(o => (
            <tr key={o.id} style={{ borderBottom: '1px solid #f8fafc', transition: 'background 0.15s' }}>
              <td style={{ padding: '12px 10px', fontWeight: '700', color: '#64748b' }}>#{o.id}</td>
              {!filterCustomerId && (
                <td style={{ padding: '12px 10px', color: NAVY, fontWeight: '600' }}>#{o.customerId}</td>
              )}
              <td style={{ padding: '12px 10px', fontWeight: '700', color: NAVY }}>{formatMoney(o.total)}</td>
              <td style={{ padding: '12px 10px' }}><StatusPill status={o.status} /></td>
              <td style={{ padding: '12px 10px', textAlign: 'center', display: 'flex', gap: '8px', justifyContent: 'center' }}>
                <button
                  onClick={() => onSelect(o)}
                  style={{
                    background: '#f1f5f9', color: NAVY, border: 'none', borderRadius: '6px',
                    padding: '6px 12px', fontSize: '0.75rem', fontWeight: '700', cursor: 'pointer',
                    transition: 'all 0.15s'
                  }}
                  onMouseEnter={e => { e.currentTarget.style.background = NAVY; e.currentTarget.style.color = '#fff'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = '#f1f5f9'; e.currentTarget.style.color = NAVY; }}
                >
                  Ver Detalle
                </button>
                <button
                  onClick={async () => {
                    if (window.confirm('¿Seguro que deseas eliminar este pedido?')) {
                      try {
                        const { deleteOrder } = await import('../services/ordersService');
                        await deleteOrder(o.id);
                        setOrders(prev => prev.filter(order => order.id !== o.id));
                      } catch (err) {
                        alert('Error al eliminar el pedido.');
                      }
                    }
                  }}
                  style={{
                    background: '#fee2e2', color: '#b91c1c', border: 'none', borderRadius: '6px',
                    padding: '6px 12px', fontSize: '0.75rem', fontWeight: '700', cursor: 'pointer',
                    transition: 'all 0.15s'
                  }}
                  onMouseEnter={e => { e.currentTarget.style.background = '#b91c1c'; e.currentTarget.style.color = '#fff'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = '#fee2e2'; e.currentTarget.style.color = '#b91c1c'; }}
                >
                  Eliminar
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
