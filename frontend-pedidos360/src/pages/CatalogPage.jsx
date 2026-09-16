import { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal } from '@azure/msal-react';
import ProductCard from '../components/ProductCard';
import ProductForm from '../components/ProductForm';
import { getProducts, decreaseStock } from '../services/catalogService';
import { createOrder } from '../services/ordersService';
import useUserRoles from '../hooks/useUserRoles';
import { getCustomerIdFromAccount, formatMoney } from '../utils/userUtils';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function CatalogPage() {
  const { accounts } = useMsal();
  const userRoles = useUserRoles();
  const navigate = useNavigate();

  const isAdmin = userRoles.includes('Admin');
  const account = accounts[0];
  const customerId = getCustomerIdFromAccount(account);
  const customerName = account?.name || account?.username || 'Cliente';

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Modales de administración
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);

  // Modal de compra cliente
  const [buyingProduct, setBuyingProduct] = useState(null);
  const [buyingQty, setBuyingQty] = useState(1);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseSuccessOrder, setPurchaseSuccessOrder] = useState(null);

  const fetchProducts = useCallback(() => {
    setLoading(true);
    getProducts()
      .then(res => setProducts(Array.isArray(res.data) ? res.data : []))
      .catch(() => setError('Error al cargar el catálogo desde el servidor.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

  // Manejadores admin
  const handleEdit = (product) => {
    setEditingProduct(product);
    setShowForm(true);
  };

  const handleAddNew = () => {
    setEditingProduct(null);
    setShowForm(true);
  };

  const handleFormClose = (changed) => {
    setShowForm(false);
    if (changed) fetchProducts();
  };

  // Manejadores cliente
  const handleStartBuy = (product, quantity) => {
    setBuyingProduct(product);
    setBuyingQty(quantity);
    setPurchaseSuccessOrder(null);
  };

  const handleConfirmPurchase = async () => {
    if (!buyingProduct) return;
    setPurchasing(true);

    const orderTotal = buyingProduct.price * buyingQty;
    const payload = {
      customerId: customerId,
      total: orderTotal,
      items: [
        {
          productId: buyingProduct.id,
          quantity: buyingQty,
          unitPrice: buyingProduct.price,
        }
      ]
    };

    try {
      const orderRes = await createOrder(payload);
      // Descontar stock en el catálogo
      await decreaseStock(buyingProduct.id, buyingQty);

      setPurchaseSuccessOrder(orderRes.data);
      fetchProducts(); // refrescar stock en pantalla
    } catch (err) {
      console.error('Error al realizar compra ficticia:', err);
      alert('Ocurrió un error al procesar la compra. Revise la consola o la conexión del servidor.');
    } finally {
      setPurchasing(false);
    }
  };

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box', overflowY: 'auto' }}>
      
      {/* ── Encabezado ─────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '28px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '4px' }}>
            <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: 0 }}>
              {isAdmin ? 'Catálogo e Inventario' : 'Catálogo de Productos'}
            </h1>
            <span style={{
              background: isAdmin ? 'rgba(0,48,135,0.1)' : 'rgba(255,107,0,0.12)',
              color: isAdmin ? NAVY : ORANGE,
              fontSize: '0.75rem', fontWeight: '800', padding: '4px 10px', borderRadius: '12px',
              textTransform: 'uppercase', letterSpacing: '0.04em'
            }}>
              {isAdmin ? 'Vista Administrador' : 'Vista Cliente'}
            </span>
          </div>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>
            {isAdmin 
              ? 'Administre el inventario, agregue nuevos productos o modifique precios y existencias.'
              : 'Explore los productos disponibles en la base de datos y realice compras ficticias en tiempo real.'}
          </p>
        </div>

        {isAdmin ? (
          <button 
            onClick={handleAddNew}
            style={{
              background: ORANGE, color: '#fff', border: 'none', borderRadius: '8px',
              padding: '10px 20px', fontWeight: '700', fontSize: '0.9rem', cursor: 'pointer',
              display: 'flex', alignItems: 'center', gap: '8px',
              boxShadow: '0 4px 12px rgba(255,107,0,0.3)', transition: 'background 0.2s'
            }}
            onMouseEnter={e => e.currentTarget.style.background = '#e66000'}
            onMouseLeave={e => e.currentTarget.style.background = ORANGE}
          >
            <span>+</span> Nuevo Producto
          </button>
        ) : (
          <div style={{
            background: '#fff', border: '1px solid #e2e8f0', padding: '8px 16px',
            borderRadius: '10px', display: 'flex', alignItems: 'center', gap: '10px',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
          }}>
            <span style={{ fontSize: '1.2rem' }}>👤</span>
            <div style={{ fontSize: '0.82rem' }}>
              <div style={{ fontWeight: '700', color: NAVY }}>{customerName}</div>
              <div style={{ color: '#64748b' }}>ID Cliente: #{customerId}</div>
            </div>
          </div>
        )}
      </div>

      {/* ── Grid de Productos ─────────────────────────────────────────── */}
      {loading ? (
        <div style={{ padding: '48px 0', color: '#64748b', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span>⏳</span> Cargando catálogo de productos...
        </div>
      ) : error ? (
        <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '16px', borderRadius: '10px' }}>
          {error}
        </div>
      ) : products.length === 0 ? (
        <div style={{ background: '#fff', borderRadius: '14px', padding: '48px', textAlign: 'center', color: '#94a3b8' }}>
          <div style={{ fontSize: '2.5rem', marginBottom: '12px' }}>📦</div>
          <p style={{ margin: 0, fontWeight: '600' }}>No hay productos registrados en la base de datos.</p>
          {isAdmin && (
            <p style={{ margin: '8px 0 0', fontSize: '0.85rem' }}>
              Haga clic en <strong>"+ Nuevo Producto"</strong> para poblar la base de datos.
            </p>
          )}
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: '22px' }}>
          {products.map(p => (
            <ProductCard 
              key={p.id} 
              product={p} 
              isAdmin={isAdmin} 
              onEdit={handleEdit} 
              onBuy={handleStartBuy} 
            />
          ))}
        </div>
      )}

      {/* ── Modal Admin: Crear / Editar Producto ────────────────────────── */}
      {showForm && (
        <ProductForm productToEdit={editingProduct} onClose={handleFormClose} />
      )}

      {/* ── Modal Cliente: Confirmación de Compra Ficticia ─────────────── */}
      {buyingProduct && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <div style={modalHeaderStyle}>
              <h2 style={{ margin: 0, fontSize: '1.2rem', color: NAVY }}>
                {purchaseSuccessOrder ? '🎉 ¡Compra Confirmada!' : '🛍️ Confirmar Pedido Ficticio'}
              </h2>
              <button 
                onClick={() => { setBuyingProduct(null); setPurchaseSuccessOrder(null); }} 
                style={closeBtnStyle}
              >
                ✕
              </button>
            </div>

            <div style={{ padding: '24px' }}>
              {purchaseSuccessOrder ? (
                <div>
                  <div style={{ textAlign: 'center', padding: '12px 0 24px' }}>
                    <div style={{ fontSize: '3.5rem', marginBottom: '8px' }}>✅</div>
                    <h3 style={{ margin: '0 0 8px', color: NAVY, fontSize: '1.3rem' }}>
                      Pedido #{purchaseSuccessOrder.id} Generado
                    </h3>
                    <p style={{ color: '#64748b', fontSize: '0.9rem', margin: 0, lineHeight: 1.5 }}>
                      Su pedido se ha registrado en el sistema con estado <strong>CREADO</strong>.<br />
                      El administrador puede gestionarlo y despacharlo desde el panel admin.
                    </p>
                  </div>

                  <div style={{ background: '#f8fafc', borderRadius: '10px', padding: '16px', marginBottom: '24px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.88rem' }}>
                      <span style={{ color: '#64748b' }}>Producto:</span>
                      <strong style={{ color: NAVY }}>{buyingProduct.name} (x{buyingQty})</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.88rem' }}>
                      <span style={{ color: '#64748b' }}>Cliente:</span>
                      <span style={{ color: NAVY }}>{customerName} (#{customerId})</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '8px', borderTop: '1px solid #e2e8f0', fontSize: '1.05rem' }}>
                      <span style={{ fontWeight: '700', color: NAVY }}>Total Pagado:</span>
                      <strong style={{ color: ORANGE }}>{formatMoney(buyingProduct.price * buyingQty)}</strong>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '12px' }}>
                    <button 
                      onClick={() => { setBuyingProduct(null); setPurchaseSuccessOrder(null); }}
                      style={{ ...btnSecondaryStyle, flex: 1 }}
                    >
                      Seguir Comprando
                    </button>
                    <button 
                      onClick={() => navigate('/orders')}
                      style={{ ...btnPrimaryStyle, flex: 1 }}
                    >
                      📦 Ver en Mis Pedidos
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <p style={{ margin: '0 0 16px', fontSize: '0.9rem', color: '#475569' }}>
                    Está a punto de realizar una compra ficticia para probar el flujo de Pedidos360:
                  </p>

                  <div style={{ background: '#f8fafc', borderRadius: '10px', padding: '16px', marginBottom: '20px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ color: '#64748b', fontSize: '0.88rem' }}>Producto:</span>
                      <strong style={{ color: NAVY, fontSize: '0.92rem' }}>{buyingProduct.name}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ color: '#64748b', fontSize: '0.88rem' }}>Precio unitario:</span>
                      <span style={{ color: '#475569', fontSize: '0.88rem' }}>{formatMoney(buyingProduct.price)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ color: '#64748b', fontSize: '0.88rem' }}>Cantidad seleccionada:</span>
                      <strong style={{ color: NAVY, fontSize: '0.88rem' }}>{buyingQty} un.</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                      <span style={{ color: '#64748b', fontSize: '0.88rem' }}>ID Cliente (Automático):</span>
                      <span style={{ color: '#64748b', fontSize: '0.88rem' }}>#{customerId}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: '10px', borderTop: '1px solid #e2e8f0', marginTop: '10px' }}>
                      <span style={{ fontWeight: '700', color: NAVY }}>Total del Pedido:</span>
                      <strong style={{ color: ORANGE, fontSize: '1.2rem' }}>{formatMoney(buyingProduct.price * buyingQty)}</strong>
                    </div>
                  </div>

                  <div style={{ background: 'rgba(255,107,0,0.08)', borderRadius: '8px', padding: '12px 14px', marginBottom: '24px', borderLeft: `3px solid ${ORANGE}` }}>
                    <p style={{ margin: 0, fontSize: '0.8rem', color: '#9a3412', lineHeight: 1.4 }}>
                      💡 Al confirmar, se creará un registro de orden en el microservicio <strong>orders</strong> y se descontará el stock en <strong>catalog</strong>.
                    </p>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                    <button 
                      type="button" 
                      onClick={() => setBuyingProduct(null)} 
                      style={btnSecondaryStyle}
                      disabled={purchasing}
                    >
                      Cancelar
                    </button>
                    <button 
                      type="button" 
                      onClick={handleConfirmPurchase} 
                      style={btnPrimaryStyle}
                      disabled={purchasing}
                    >
                      {purchasing ? 'Procesando Compra...' : 'Confirmar Compra'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

const overlayStyle = {
  position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
  background: 'rgba(0,48,135,0.4)', backdropFilter: 'blur(4px)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 999
};

const modalStyle = {
  background: '#fff', borderRadius: '16px', width: '480px', maxWidth: '90%',
  boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)', overflow: 'hidden'
};

const modalHeaderStyle = {
  padding: '20px 24px', background: '#f8fafc', borderBottom: '1px solid #e2e8f0',
  display: 'flex', justifyContent: 'space-between', alignItems: 'center'
};

const closeBtnStyle = { background: 'none', border: 'none', fontSize: '1.2rem', color: '#94a3b8', cursor: 'pointer' };

const btnPrimaryStyle = {
  padding: '12px 20px', borderRadius: '8px', border: 'none',
  background: ORANGE, color: '#fff', fontWeight: '700', fontSize: '0.9rem',
  cursor: 'pointer', textAlign: 'center', textDecoration: 'none'
};

const btnSecondaryStyle = {
  padding: '12px 20px', borderRadius: '8px', border: '1px solid #cbd5e1',
  background: '#fff', color: '#64748b', fontWeight: '600', fontSize: '0.9rem',
  cursor: 'pointer', textAlign: 'center', textDecoration: 'none'
};

export default CatalogPage;
