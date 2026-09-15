import { useState, useEffect } from 'react';
import { getProducts } from '../services/catalogService';
import { createOrder, updateOrder } from '../services/ordersService';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function OrderForm({ onClose, onCreated, initialOrder }) {
  const [products, setProducts] = useState([]);
  const [customerId, setCustomerId] = useState('');
  const [items, setItems] = useState([]); 
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    getProducts().then(res => {
      const allProducts = res.data;
      setProducts(allProducts);

      if (initialOrder) {
        setCustomerId(initialOrder.customerId);
        if (initialOrder.items) {
          const mappedItems = initialOrder.items.map(i => {
            const prod = allProducts.find(p => p.id === i.productId) || { id: i.productId, name: `Producto ${i.productId}`, price: i.unitPrice, stock: 999 };
            return { product: prod, quantity: i.quantity };
          });
          setItems(mappedItems);
        }
      }
    }).catch(console.error);
  }, [initialOrder]);

  const handleAddItem = (productId) => {
    if (!productId) return;
    const product = products.find(p => p.id === parseInt(productId));
    if (!product) return;
    
    setItems(prev => {
      const existing = prev.find(i => i.product.id === product.id);
      if (existing) {
        return prev.map(i => i.product.id === product.id ? { ...i, quantity: i.quantity + 1 } : i);
      }
      return [...prev, { product, quantity: 1 }];
    });
  };

  const handleRemoveItem = (productId) => {
    setItems(prev => prev.filter(i => i.product.id !== productId));
  };

  const handleQuantityChange = (productId, qty) => {
    if (qty < 1) return;
    setItems(prev => prev.map(i => i.product.id === productId ? { ...i, quantity: qty } : i));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!customerId || items.length === 0) {
      setError('Ingrese un cliente y al menos un producto.');
      return;
    }

    setLoading(true);
    const payload = {
      customerId: parseInt(customerId),
      total: items.reduce((acc, i) => acc + (i.product.price * i.quantity), 0),
      items: items.map(i => ({
        productId: i.product.id,
        quantity: i.quantity,
        unitPrice: i.product.price
      }))
    };

    try {
      if (initialOrder) {
        await updateOrder(initialOrder.id, payload);
      } else {
        await createOrder(payload);
        const { decreaseStock } = await import('../services/catalogService');
        await Promise.all(items.map(i => decreaseStock(i.product.id, i.quantity)));
      }
      onCreated();
    } catch (err) {
      setError(`Error al ${initialOrder ? 'actualizar' : 'crear'} el pedido.`);
    } finally {
      setLoading(false);
    }
  };

  const total = items.reduce((acc, i) => acc + (i.product.price * i.quantity), 0);

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <div style={headerStyle}>
          <h2 style={{ margin: 0, fontSize: '1.2rem', color: NAVY }}>{initialOrder ? 'Editar Pedido' : 'Nuevo Pedido'}</h2>
          <button onClick={onClose} style={closeBtnStyle}>✕</button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: '24px' }}>
          {error && <p style={{ color: '#dc2626', fontSize: '0.85rem', marginBottom: '16px' }}>{error}</p>}
          
          <div style={{ marginBottom: '20px' }}>
            <label style={labelStyle}>ID Cliente</label>
            <input 
              type="number" 
              value={customerId} 
              onChange={e => setCustomerId(e.target.value)} 
              placeholder="Ej. 101"
              style={inputStyle}
              required 
            />
          </div>

          <div style={{ marginBottom: '20px' }}>
            <label style={labelStyle}>Agregar Producto</label>
            <select 
              onChange={e => { handleAddItem(e.target.value); e.target.value = ""; }}
              style={inputStyle}
              defaultValue=""
            >
              <option value="" disabled>-- Seleccione un producto --</option>
              {products.map(p => (
                <option key={p.id} value={p.id}>{p.name} (${p.price}) - Stock: {p.stock}</option>
              ))}
            </select>
          </div>

          {items.length > 0 && (
            <div style={{ marginBottom: '20px', background: '#f8fafc', padding: '16px', borderRadius: '8px' }}>
              <h4 style={{ margin: '0 0 12px', fontSize: '0.9rem', color: NAVY }}>Items del Pedido</h4>
              {items.map(item => (
                <div key={item.product.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                  <div style={{ flex: 1, fontSize: '0.85rem', color: '#475569' }}>
                    {item.product.name}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <input 
                      type="number" 
                      value={item.quantity} 
                      onChange={e => handleQuantityChange(item.product.id, parseInt(e.target.value))}
                      style={{ ...inputStyle, width: '60px', padding: '4px' }}
                      min="1"
                    />
                    <button type="button" onClick={() => handleRemoveItem(item.product.id)} style={{ color: '#dc2626', background: 'none', border: 'none', cursor: 'pointer' }}>✕</button>
                  </div>
                </div>
              ))}
              <div style={{ marginTop: '12px', paddingTop: '12px', borderTop: '1px solid #cbd5e1', textAlign: 'right', fontWeight: 'bold', color: NAVY }}>
                Total: ${total.toFixed(2)}
              </div>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '32px' }}>
            <button type="button" onClick={onClose} style={btnCancelStyle}>Cancelar</button>
            <button type="submit" style={btnSubmitStyle} disabled={loading}>
              {loading ? 'Guardando...' : (initialOrder ? 'Guardar Cambios' : 'Crear Pedido')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

const overlayStyle = {
  position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh',
  background: 'rgba(0,48,135,0.4)', backdropFilter: 'blur(4px)',
  display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 999
};

const modalStyle = {
  background: '#fff', borderRadius: '16px', width: '500px', maxWidth: '90%',
  boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)', overflow: 'hidden'
};

const headerStyle = {
  padding: '20px 24px', background: '#f8fafc', borderBottom: '1px solid #e2e8f0',
  display: 'flex', justifyContent: 'space-between', alignItems: 'center'
};

const labelStyle = { display: 'block', fontSize: '0.85rem', fontWeight: '600', color: NAVY, marginBottom: '6px' };
const inputStyle = { width: '100%', padding: '10px 12px', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '0.9rem' };

const closeBtnStyle = { background: 'none', border: 'none', fontSize: '1.2rem', color: '#94a3b8', cursor: 'pointer' };
const btnCancelStyle = { padding: '10px 20px', borderRadius: '8px', border: '1px solid #cbd5e1', background: '#fff', color: '#64748b', fontWeight: '600', cursor: 'pointer' };
const btnSubmitStyle = { padding: '10px 20px', borderRadius: '8px', border: 'none', background: ORANGE, color: '#fff', fontWeight: '600', cursor: 'pointer' };

export default OrderForm;
