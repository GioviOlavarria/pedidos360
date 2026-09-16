import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import OrderList from '../components/OrderList';
import OrderDetail from '../components/OrderDetail';
import OrderForm from '../components/OrderForm';
import useUserRoles from '../hooks/useUserRoles';
import { getCustomerIdFromAccount } from '../utils/userUtils';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function OrdersPage() {
  const { accounts } = useMsal();
  const userRoles = useUserRoles();
  const navigate = useNavigate();

  const isAdmin = userRoles.includes('Admin');
  const account = accounts[0];
  const customerId = getCustomerIdFromAccount(account);

  const [selectedOrderId, setSelectedOrderId] = useState(null);
  const [listKey, setListKey] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editingOrder, setEditingOrder] = useState(null);

  const handleSelect = useCallback((order) => {
    setSelectedOrderId(order.id);
  }, []);

  const handleRefresh = useCallback(() => {
    setListKey(k => k + 1);
  }, []);

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
      
      {/* ── Encabezado ─────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '4px' }}>
            <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: 0 }}>
              {isAdmin ? 'Gestión de Pedidos' : 'Mis Pedidos'}
            </h1>
            <span style={{
              background: isAdmin ? 'rgba(0,48,135,0.1)' : 'rgba(16,185,129,0.12)',
              color: isAdmin ? NAVY : '#059669',
              fontSize: '0.75rem', fontWeight: '800', padding: '4px 10px', borderRadius: '12px',
              textTransform: 'uppercase', letterSpacing: '0.04em'
            }}>
              {isAdmin ? 'Vista Administrador' : 'Vista Cliente'}
            </span>
          </div>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>
            {isAdmin 
              ? 'Administre el flujo y avance de estado de todos los pedidos del sistema.' 
              : 'Haga seguimiento en tiempo real al estado de sus compras ficticias.'}
          </p>
        </div>

        <div style={{ display: 'flex', gap: '12px' }}>
          <button 
            onClick={handleRefresh}
            style={{
              background: '#fff', color: NAVY, border: '1px solid #cbd5e1', borderRadius: '8px',
              padding: '10px 16px', fontWeight: '600', fontSize: '0.88rem', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: '6px',
            }}
          >
            🔄 Actualizar
          </button>

          {isAdmin ? (
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
          ) : (
            <button 
              onClick={() => navigate('/catalog')}
              style={{
                background: ORANGE, color: '#fff', border: 'none', borderRadius: '8px',
                padding: '10px 20px', fontWeight: '700', fontSize: '0.9rem',
                cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
                boxShadow: '0 4px 12px rgba(255,107,0,0.3)'
              }}
            >
              🛍️ Explorar Catálogo
            </button>
          )}
        </div>
      </div>

      {/* ── Contenido Principal (Lista + Detalle) ──────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 440px', gap: '24px', flex: 1, alignItems: 'start' }}>
        <section style={{ 
          background: '#fff', borderRadius: '14px', padding: '24px', 
          boxShadow: '0 1px 4px rgba(0,0,0,0.06)', borderTop: `3px solid ${NAVY}`
        }}>
          <h2 style={{ fontSize: '1rem', fontWeight: '700', color: NAVY, marginBottom: '16px' }}>
            {isAdmin ? 'Listado general de pedidos' : 'Historial de mis pedidos'}
          </h2>
          <OrderList 
            key={listKey} 
            onSelect={handleSelect} 
            filterCustomerId={isAdmin ? null : customerId} 
          />
        </section>

        <section style={{ position: 'sticky', top: '24px' }}>
          {selectedOrderId ? (
            <OrderDetail 
              orderId={selectedOrderId} 
              onStatusChanged={handleRefresh} 
              onEdit={isAdmin ? ((order) => { setEditingOrder(order); setShowForm(true); }) : null}
            />
          ) : (
            <div style={{
              background: '#f8fafc', border: '2px dashed #cbd5e1', borderRadius: '14px',
              padding: '48px 24px', textAlign: 'center', color: '#94a3b8'
            }}>
              <div style={{ fontSize: '2.5rem', marginBottom: '16px' }}>📦</div>
              <p style={{ margin: 0, fontWeight: '500' }}>
                Seleccione un pedido de la lista para ver su seguimiento y detalle.
              </p>
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
