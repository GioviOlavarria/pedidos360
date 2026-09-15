import { useState, useCallback, useEffect } from 'react';
import ProductCard from '../components/ProductCard';
import ProductForm from '../components/ProductForm';
import { getProducts } from '../services/catalogService';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function CatalogPage() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [showForm, setShowForm] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null);

  const fetchProducts = useCallback(() => {
    setLoading(true);
    getProducts()
      .then(res => setProducts(res.data))
      .catch(() => setError('Error al cargar el catálogo.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    fetchProducts();
  }, [fetchProducts]);

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

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
        <div>
          <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: '0 0 4px' }}>Catálogo de Productos</h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>Gestione el inventario y precios de sus productos.</p>
        </div>
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
      </div>

      {loading ? (
        <p style={{ color: '#94a3b8' }}>Cargando catálogo...</p>
      ) : error ? (
        <p style={{ color: '#ef4444' }}>{error}</p>
      ) : products.length === 0 ? (
        <div style={{ background: '#fff', borderRadius: '14px', padding: '48px', textAlign: 'center', color: '#94a3b8' }}>
          No hay productos registrados.
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '20px' }}>
          {products.map(p => (
            <ProductCard key={p.id} product={p} onEdit={() => handleEdit(p)} />
          ))}
        </div>
      )}

      {showForm && (
        <ProductForm productToEdit={editingProduct} onClose={handleFormClose} />
      )}
    </div>
  );
}

export default CatalogPage;
