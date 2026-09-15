import { useState, useEffect } from 'react';
import { getOrderById, updateOrderStatus } from '../services/ordersService';
import useUserRoles from '../hooks/useUserRoles';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

const NEXT_STATUS = {
  CREADO: 'ACEPTADO',
  ACEPTADO: 'EN_PREPARACION',
  EN_PREPARACION: 'DESPACHADO',
  DESPACHADO: 'ENTREGADO',
  ENTREGADO: null,
  CANCELADO: null,
};

function OrderDetail({ orderId, onStatusChanged }) {
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const userRoles = useUserRoles();
  const canUpdate = userRoles.some(r => r === 'Admin' || r === 'Operator');

  useEffect(() => {
    setLoading(true);
    getOrderById(orderId)
      .then(res => setOrder(res.data))
      .catch(() => setError('Error al cargar detalle del pedido'))
      .finally(() => setLoading(false));
  }, [orderId]);

  if (loading) return <div style={cardStyle}><p style={{ color: '#94a3b8', margin: 0 }}>Cargando detalle...</p></div>;
  if (error) return <div style={cardStyle}><p style={{ color: '#ef4444', margin: 0 }}>{error}</p></div>;
  if (!order) return null;

  const handleUpdateStatus = (newStatus) => {
    updateOrderStatus(orderId, newStatus)
      .then(() => {
        setOrder(prev => ({ ...prev, status: newStatus }));
        if (onStatusChanged) onStatusChanged();
      })
      .catch(() => alert('Error al cambiar el estado.'));
  };

  const nextStatus = NEXT_STATUS[order.status];

  return (
    <div style={cardStyle}>
      <div style={{ borderBottom: '1px solid #f1f5f9', paddingBottom: '16px', marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ fontSize: '1.2rem', fontWeight: '800', color: NAVY, margin: 0 }}>Pedido #{order.id}</h2>
        <span style={{ fontSize: '0.8rem', color: '#64748b', fontWeight: '600' }}>{new Date(order.createdAt).toLocaleDateString()}</span>
      </div>

      <div style={{ marginBottom: '24px' }}>
        <p style={{ margin: '0 0 8px', fontSize: '0.9rem', color: '#475569' }}>
          <strong>Cliente:</strong> {order.customerId}
        </p>
        <p style={{ margin: '0 0 8px', fontSize: '0.9rem', color: '#475569' }}>
          <strong>Estado Actual:</strong> {order.status}
        </p>
        <p style={{ margin: '0 0 8px', fontSize: '1.1rem', color: NAVY }}>
          <strong>Total:</strong> ${order.total}
        </p>
      </div>

      <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: '20px' }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: '700', color: NAVY, marginBottom: '12px', textTransform: 'uppercase' }}>Acciones</h3>
        
        {!canUpdate ? (
          <p style={{ fontSize: '0.85rem', color: '#94a3b8', margin: 0 }}>No tiene permisos para modificar el estado.</p>
        ) : (
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            {nextStatus && (
              <button 
                onClick={() => handleUpdateStatus(nextStatus)}
                style={btnPrimaryStyle}
              >
                Avanzar a {nextStatus}
              </button>
            )}
            
            {order.status !== 'ENTREGADO' && order.status !== 'CANCELADO' && (
              <button 
                onClick={() => handleUpdateStatus('CANCELADO')}
                style={btnDangerStyle}
              >
                Cancelar Pedido
              </button>
            )}
            
            {!nextStatus && order.status !== 'CANCELADO' && (
              <p style={{ fontSize: '0.85rem', color: '#15803d', margin: 0, fontWeight: '600' }}>✅ Flujo completado</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

const cardStyle = {
  background: '#fff', borderRadius: '14px', padding: '24px', 
  boxShadow: '0 4px 16px rgba(0,48,135,0.08)', borderTop: `4px solid ${ORANGE}`
};

const btnPrimaryStyle = {
  background: NAVY, color: '#fff', border: 'none', borderRadius: '8px',
  padding: '8px 16px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
};

const btnDangerStyle = {
  background: '#fee2e2', color: '#b91c1c', border: 'none', borderRadius: '8px',
  padding: '8px 16px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
};

export default OrderDetail;
