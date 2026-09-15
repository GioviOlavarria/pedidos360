const NAVY = '#003087';
const ORANGE = '#FF6B00';

function ProductCard({ product, onEdit }) {
  const stockColor = product.stock > 10 ? '#15803d' : product.stock > 0 ? '#b45309' : '#b91c1c';
  const stockBg    = product.stock > 10 ? '#dcfce7' : product.stock > 0 ? '#fef3c7' : '#fee2e2';
  const stockText  = product.stock > 10 ? 'En stock' : product.stock > 0 ? 'Poco stock' : 'Sin stock';

  return (
    <div style={{
      background: '#fff', borderRadius: '14px', padding: '24px',
      boxShadow: '0 4px 16px rgba(0,48,135,0.06)', borderTop: `3px solid ${NAVY}`,
      display: 'flex', flexDirection: 'column'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '12px' }}>
        <h3 style={{ margin: 0, fontSize: '1.1rem', color: NAVY, fontWeight: '700', lineHeight: 1.2 }}>
          {product.name}
        </h3>
        <span style={{ 
          background: stockBg, color: stockColor, padding: '4px 10px', 
          borderRadius: '20px', fontSize: '0.7rem', fontWeight: '700', whiteSpace: 'nowrap' 
        }}>
          {stockText}
        </span>
      </div>
      
      <p style={{ margin: '0 0 16px', fontSize: '0.85rem', color: '#64748b', flex: 1 }}>
        {product.description}
      </p>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
        <div>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '2px' }}>Precio Unitario</div>
          <div style={{ fontSize: '1.3rem', fontWeight: '800', color: ORANGE }}>
            ${product.price}
          </div>
        </div>
        
        <div>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '2px', textAlign: 'right' }}>Disp.</div>
          <div style={{ fontSize: '1rem', fontWeight: '700', color: NAVY, textAlign: 'right' }}>
            {product.stock} un.
          </div>
        </div>
      </div>
      
      <button 
        onClick={onEdit}
        style={{
          width: '100%', marginTop: '16px', padding: '8px',
          background: '#f8fafc', color: NAVY, border: '1px solid #e2e8f0',
          borderRadius: '8px', fontWeight: '600', fontSize: '0.85rem', cursor: 'pointer',
          transition: 'all 0.2s'
        }}
        onMouseEnter={e => { e.currentTarget.style.background = NAVY; e.currentTarget.style.color = '#fff'; e.currentTarget.style.borderColor = NAVY; }}
        onMouseLeave={e => { e.currentTarget.style.background = '#f8fafc'; e.currentTarget.style.color = NAVY; e.currentTarget.style.borderColor = '#e2e8f0'; }}
      >
        Editar Producto
      </button>
    </div>
  );
}

export default ProductCard;
