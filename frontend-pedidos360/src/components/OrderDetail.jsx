import { useState, useEffect } from 'react';
import { getOrderById, updateOrderStatus } from '../services/ordersService';
import { getProducts } from '../services/catalogService';
import useUserRoles from '../hooks/useUserRoles';
import { formatMoney } from '../utils/userUtils';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

const STATUS_STEPS = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO'];

const STATUS_LABELS = {
  CREADO: 'Creado',
  ACEPTADO: 'Aceptado',
  EN_PREPARACION: 'En preparación',
  DESPACHADO: 'Despachado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

const NEXT_STATUS = {
  CREADO: 'ACEPTADO',
  ACEPTADO: 'EN_PREPARACION',
  EN_PREPARACION: 'DESPACHADO',
  DESPACHADO: 'ENTREGADO',
  ENTREGADO: null,
  CANCELADO: null,
};

function OrderDetail({ orderId, onStatusChanged, onEdit }) {
  const [order, setOrder] = useState(null);
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const userRoles = useUserRoles();
  const isAdmin = userRoles.includes('Admin');
  const canUpdate = userRoles.some(r => r === 'Admin' || r === 'Operator');

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getOrderById(orderId),
      getProducts().catch(() => ({ data: [] }))
    ])
      .then(([orderRes, prodRes]) => {
        setOrder(orderRes.data);
        setProducts(Array.isArray(prodRes.data) ? prodRes.data : []);
      })
      .catch(() => setError('Error al cargar detalle del pedido'))
      .finally(() => setLoading(false));
  }, [orderId]);

  if (loading) {
    return (
      <div style={cardStyle}>
        <p style={{ color: '#94a3b8', margin: 0 }}>Cargando detalle del pedido #{orderId}...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div style={cardStyle}>
        <p style={{ color: '#ef4444', margin: 0 }}>{error}</p>
      </div>
    );
  }

  if (!order) return null;

  const handleUpdateStatus = (newStatus) => {
    updateOrderStatus(orderId, newStatus)
      .then(() => {
        setOrder(prev => ({ ...prev, status: newStatus }));
        if (onStatusChanged) onStatusChanged();
      })
      .catch(() => alert('Error al cambiar el estado del pedido. Verifique la regla de transición de estados.'));
  };

  const nextStatus = NEXT_STATUS[order.status];
  const currentStepIndex = STATUS_STEPS.indexOf(order.status);
  const isCancelled = order.status === 'CANCELADO';

  return (
    <div style={cardStyle}>
      {/* ── Encabezado ────────────────────────────────────────────── */}
      <div style={{ borderBottom: '1px solid #f1f5f9', paddingBottom: '16px', marginBottom: '20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: '800', color: NAVY, margin: 0 }}>
            Pedido #{order.id}
          </h2>
          <span style={{ fontSize: '0.78rem', color: '#64748b' }}>
            Registrado el {new Date(order.createdAt).toLocaleDateString()} a las {new Date(order.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>
        <span style={{
          background: isCancelled ? '#fee2e2' : '#dbeafe',
          color: isCancelled ? '#b91c1c' : '#1d4ed8',
          padding: '6px 12px', borderRadius: '20px',
          fontSize: '0.75rem', fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.04em'
        }}>
          {STATUS_LABELS[order.status] || order.status}
        </span>
      </div>

      {/* ── Barra de Seguimiento Visual (Tracking Timeline) ─────────── */}
      <div style={{ marginBottom: '24px', background: '#f8fafc', padding: '16px', borderRadius: '12px', border: '1px solid #e2e8f0' }}>
        <h4 style={{ margin: '0 0 12px', fontSize: '0.8rem', textTransform: 'uppercase', letterSpacing: '0.06em', color: '#64748b' }}>
          Seguimiento de Entrega
        </h4>

        {isCancelled ? (
          <div style={{ color: '#b91c1c', fontWeight: '700', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>❌</span> Pedido cancelado
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'relative' }}>
            {STATUS_STEPS.map((step, idx) => {
              const isPassed = idx <= currentStepIndex;
              const isCurrent = idx === currentStepIndex;
              return (
                <div key={step} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 1, position: 'relative' }}>
                  <div style={{
                    width: '28px', height: '28px', borderRadius: '50%',
                    background: isCurrent ? ORANGE : isPassed ? '#10b981' : '#e2e8f0',
                    color: isPassed ? '#fff' : '#94a3b8',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    fontSize: '0.75rem', fontWeight: '800', zIndex: 2,
                    boxShadow: isCurrent ? '0 0 0 4px rgba(255,107,0,0.2)' : 'none',
                    transition: 'all 0.3s ease'
                  }}>
                    {isPassed && !isCurrent ? '✓' : idx + 1}
                  </div>
                  <span style={{
                    fontSize: '0.68rem', marginTop: '6px', textAlign: 'center',
                    color: isCurrent ? NAVY : isPassed ? '#10b981' : '#94a3b8',
                    fontWeight: isCurrent ? '800' : '600'
                  }}>
                    {STATUS_LABELS[step]}
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ── Datos del Pedido ────────────────────────────────────────── */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.88rem' }}>
          <span style={{ color: '#64748b' }}>Cliente ID:</span>
          <strong style={{ color: NAVY }}>#{order.customerId}</strong>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.88rem' }}>
          <span style={{ color: '#64748b' }}>Estado Actual:</span>
          <strong style={{ color: isCancelled ? '#b91c1c' : NAVY }}>{order.status}</strong>
        </div>
      </div>

      {/* ── Lista de Ítems del Pedido ─────────────────────────────────── */}
      <div style={{ marginBottom: '24px', borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
        <h4 style={{ margin: '0 0 12px', fontSize: '0.85rem', fontWeight: '700', color: NAVY }}>
          Artículos en el pedido
        </h4>

        {order.items && order.items.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {order.items.map((item, i) => {
              const product = products.find(p => p.id === item.productId);
              const name = product ? product.name : `Producto #${item.productId}`;
              return (
                <div key={item.id || i} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#f8fafc', padding: '8px 12px', borderRadius: '8px', fontSize: '0.84rem' }}>
                  <div>
                    <span style={{ fontWeight: '600', color: NAVY }}>{name}</span>
                    <span style={{ color: '#64748b', marginLeft: '6px' }}>x{item.quantity}</span>
                  </div>
                  <span style={{ fontWeight: '700', color: ORANGE }}>
                    {formatMoney(item.quantity * item.unitPrice)}
                  </span>
                </div>
              );
            })}
          </div>
        ) : (
          <p style={{ margin: 0, fontSize: '0.84rem', color: '#94a3b8' }}>Sin detalle de ítems registrado.</p>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '16px', paddingTop: '12px', borderTop: '2px solid #f1f5f9' }}>
          <span style={{ fontSize: '1rem', fontWeight: '700', color: NAVY }}>Total Pagado:</span>
          <span style={{ fontSize: '1.3rem', fontWeight: '800', color: ORANGE }}>{formatMoney(order.total)}</span>
        </div>
      </div>

      {/* ── Acciones (Solo para Admin / Operador) ────────────────────── */}
      {isAdmin && (
        <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
          <h3 style={{ fontSize: '0.85rem', fontWeight: '700', color: NAVY, marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Acciones de Administración
          </h3>
          
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            {nextStatus && (
              <button 
                onClick={() => handleUpdateStatus(nextStatus)}
                style={btnPrimaryStyle}
              >
                ⏩ Avanzar a {STATUS_LABELS[nextStatus] || nextStatus}
              </button>
            )}
            
            {onEdit && (
              <button 
                onClick={() => onEdit(order)}
                style={btnSecondaryStyle}
              >
                ✏️ Editar Pedido
              </button>
            )}
            
            {order.status !== 'ENTREGADO' && order.status !== 'CANCELADO' && (
              <button 
                onClick={() => handleUpdateStatus('CANCELADO')}
                style={btnDangerStyle}
              >
                ❌ Cancelar
              </button>
            )}
            
            {!nextStatus && order.status !== 'CANCELADO' && (
              <div style={{ fontSize: '0.85rem', color: '#10b981', fontWeight: '700', display: 'flex', alignItems: 'center', gap: '6px' }}>
                ✅ Pedido completado exitosamente
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

const cardStyle = {
  background: '#fff', borderRadius: '14px', padding: '24px', 
  boxShadow: '0 4px 16px rgba(0,48,135,0.08)', borderTop: `4px solid ${ORANGE}`
};

const btnPrimaryStyle = {
  background: NAVY, color: '#fff', border: 'none', borderRadius: '8px',
  padding: '10px 16px', fontWeight: '700', fontSize: '0.85rem', cursor: 'pointer',
  boxShadow: '0 2px 6px rgba(0,48,135,0.2)'
};

const btnSecondaryStyle = {
  background: '#f1f5f9', color: NAVY, border: '1px solid #cbd5e1', borderRadius: '8px',
  padding: '10px 16px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
};

const btnDangerStyle = {
  background: '#fee2e2', color: '#b91c1c', border: 'none', borderRadius: '8px',
  padding: '10px 16px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
};

export default OrderDetail;
