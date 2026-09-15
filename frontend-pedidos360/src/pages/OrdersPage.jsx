import { useState, useCallback, useEffect } from 'react';
import OrderList from '../components/OrderList';
import OrderDetail from '../components/OrderDetail';
import OrderForm from '../components/OrderForm'; // Nuevo componente
import useUserRoles from '../hooks/useUserRoles';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function OrdersPage() {
  const [selectedOrderId, setSelectedOrderId] = useState(null);
  const [listKey, setListKey] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editingOrder, setEditingOrder] = useState(null);
  
  const userRoles = useUserRoles();
  const canCreate = userRoles.some(r => r === 'Admin' || r === 'Customer' || r === 'Operator');

  const handleSelect = useCallback((order) => {
    setSelectedOrderId(order.id);
  }, []);

  const handleRefresh = useCallback(() => {
    setListKey(k => k + 1);
  }, []);

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: '0 0 4px' }}>Gestión de Pedidos</h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>Administre el flujo y estado de los envíos.</p>
        </div>
        {canCreate && (
          <button 
            onClick={() => { setEditingOrder(null); setShowForm(true); }}
            style={{
              background: ORANGE, color: '#fff', border: 'none', borderRadius: '8px',
              padding: '10px 20px', fontWeight: '700', fontSize: '0.9rem',
              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
              boxShadow: '0 4px 12px rgba(255,107,0,0.3)', transition: 'background 0.2s'
            }}
            onMouseEnter={e => e.currentTarget.style.background = '#e66000'}
            onMouseLeave={e => e.currentTarget.style.background = ORANGE}
          >
            <span>+</span> Nuevo Pedido
          </button>
        )}
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 420px', gap: '24px', flex: 1, alignItems: 'start' }}>
        <section style={{ 
          background: '#fff', borderRadius: '14px', padding: '24px', 
          boxShadow: '0 1px 3px rgba(0,0,0,0.07)', borderTop: `3px solid ${NAVY}`
        }}>
          <h2 style={{ fontSize: '1rem', fontWeight: '700', color: NAVY, marginBottom: '16px' }}>Listado de pedidos</h2>
          <OrderList key={listKey} onSelect={handleSelect} />
        </section>

        <section style={{ position: 'sticky', top: '24px' }}>
          {selectedOrderId ? (
            <OrderDetail 
              orderId={selectedOrderId} 
              onStatusChanged={handleRefresh} 
              onEdit={(order) => { setEditingOrder(order); setShowForm(true); }}
            />
          ) : (
            <div style={{
              background: '#f8fafc', border: '2px dashed #cbd5e1', borderRadius: '14px',
              padding: '48px 24px', textAlign: 'center', color: '#94a3b8'
            }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>📦</div>
              <p style={{ margin: 0, fontWeight: '500' }}>Seleccione un pedido para ver su detalle.</p>
            </div>
          )}
        </section>
      </div>

      {showForm && (
        <OrderForm 
          initialOrder={editingOrder}
          onClose={() => { setShowForm(false); setEditingOrder(null); }} 
          onCreated={() => {
            setShowForm(false);
            setEditingOrder(null);
            handleRefresh();
          }} 
        />
      )}
    </div>
  );
}

export default OrdersPage;
