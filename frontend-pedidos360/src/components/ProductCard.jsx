import { useState } from 'react';
import { formatMoney } from '../utils/userUtils';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function ProductCard({ product, isAdmin, onEdit, onBuy }) {
  const [quantity, setQuantity] = useState(1);

  const isAvailable = product.stock > 0;
  const stockColor = product.stock > 10 ? '#15803d' : product.stock > 0 ? '#b45309' : '#b91c1c';
  const stockBg    = product.stock > 10 ? '#dcfce7' : product.stock > 0 ? '#fef3c7' : '#fee2e2';
  const stockText  = product.stock > 10 ? 'En stock' : product.stock > 0 ? `Poco stock (${product.stock})` : 'Agotado';

  const handleDecrease = () => {
    setQuantity(q => Math.max(1, q - 1));
  };

  const handleIncrease = () => {
    setQuantity(q => Math.min(product.stock, q + 1));
  };

  return (
    <div style={{
      background: '#fff', borderRadius: '16px', padding: '24px',
      boxShadow: '0 4px 20px rgba(0,48,135,0.06)', borderTop: `4px solid ${isAdmin ? NAVY : ORANGE}`,
      display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
      transition: 'transform 0.15s ease, box-shadow 0.15s ease',
    }}>
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
          <h3 style={{ margin: 0, fontSize: '1.15rem', color: NAVY, fontWeight: '700', lineHeight: 1.25 }}>
            {product.name}
          </h3>
          <span style={{ 
            background: stockBg, color: stockColor, padding: '4px 10px', 
            borderRadius: '20px', fontSize: '0.72rem', fontWeight: '700', whiteSpace: 'nowrap' 
          }}>
            {stockText}
          </span>
        </div>
        
        <p style={{ margin: '0 0 16px', fontSize: '0.86rem', color: '#64748b', lineHeight: 1.5, minHeight: '40px' }}>
          {product.description || 'Sin descripción detallada.'}
        </p>
      </div>
      
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', borderTop: '1px solid #f1f5f9', paddingTop: '14px', marginBottom: '16px' }}>
          <div>
            <div style={{ fontSize: '0.72rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '2px' }}>
              Precio Unitario
            </div>
            <div style={{ fontSize: '1.4rem', fontWeight: '800', color: ORANGE }}>
              {formatMoney(product.price)}
            </div>
          </div>
          
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.72rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '2px' }}>
              Disponible
            </div>
            <div style={{ fontSize: '1rem', fontWeight: '700', color: NAVY }}>
              {product.stock} un.
            </div>
          </div>
        </div>
        
        {isAdmin ? (
          <div style={{ display: 'flex', gap: '8px' }}>
            <button 
              onClick={() => onEdit(product)}
              style={{
                flex: 1, padding: '10px',
                background: '#f8fafc', color: NAVY, border: '1px solid #cbd5e1',
                borderRadius: '8px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                transition: 'all 0.2s'
              }}
              onMouseEnter={e => { e.currentTarget.style.background = NAVY; e.currentTarget.style.color = '#fff'; }}
              onMouseLeave={e => { e.currentTarget.style.background = '#f8fafc'; e.currentTarget.style.color = NAVY; }}
            >
              ✏️ Editar
            </button>
            <button 
              onClick={async () => {
                if (window.confirm('¿Seguro que deseas eliminar este producto?')) {
                  try {
                    const { deleteProduct } = await import('../services/catalogService');
                    await deleteProduct(product.id);
                    // Lanza un evento para que CatalogPage recargue o se quite de la lista.
                    // Para hacerlo sencillo forzamos reload.
                    window.location.reload();
                  } catch (err) {
                    alert('Error al eliminar producto');
                  }
                }
              }}
              style={{
                flex: 1, padding: '10px',
                background: '#fee2e2', color: '#b91c1c', border: '1px solid #fecaca',
                borderRadius: '8px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
                display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                transition: 'all 0.2s'
              }}
              onMouseEnter={e => { e.currentTarget.style.background = '#b91c1c'; e.currentTarget.style.color = '#fff'; }}
              onMouseLeave={e => { e.currentTarget.style.background = '#fee2e2'; e.currentTarget.style.color = '#b91c1c'; }}
            >
              🗑️ Eliminar
            </button>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {isAvailable ? (
              <>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#f8fafc', padding: '6px 12px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: '600', color: '#64748b' }}>Cantidad:</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button 
                      type="button" 
                      onClick={handleDecrease}
                      disabled={quantity <= 1}
                      style={{ width: '28px', height: '28px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      -
                    </button>
                    <span style={{ fontSize: '0.9rem', fontWeight: '700', color: NAVY, minWidth: '20px', textAlign: 'center' }}>
                      {quantity}
                    </span>
                    <button 
                      type="button" 
                      onClick={handleIncrease}
                      disabled={quantity >= product.stock}
                      style={{ width: '28px', height: '28px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      +
                    </button>
                  </div>
                </div>

                <button 
                  onClick={() => onBuy(product, quantity)}
                  style={{
                    width: '100%', padding: '12px',
                    background: ORANGE, color: '#fff', border: 'none',
                    borderRadius: '10px', fontWeight: '700', fontSize: '0.9rem', cursor: 'pointer',
                    boxShadow: '0 4px 12px rgba(255,107,0,0.25)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px',
                    transition: 'all 0.15s ease'
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = '#e66000'}
                  onMouseLeave={e => e.currentTarget.style.background = ORANGE}
                >
                  🛒 Comprar {quantity > 1 ? `(${quantity})` : ''} · {formatMoney(product.price * quantity)}
                </button>
              </>
            ) : (
              <button 
                disabled 
                style={{
                  width: '100%', padding: '12px',
                  background: '#f1f5f9', color: '#94a3b8', border: 'none',
                  borderRadius: '10px', fontWeight: '600', fontSize: '0.9rem', cursor: 'not-allowed',
                }}
              >
                Agotado temporalmente
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default ProductCard;
