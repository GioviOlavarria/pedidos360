import { useState } from 'react';
import { createProduct, updateProduct } from '../services/catalogService';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function ProductForm({ productToEdit, onClose }) {
  const isEditing = Boolean(productToEdit);
  
  const [formData, setFormData] = useState({
    name: productToEdit?.name || '',
    description: productToEdit?.description || '',
    price: productToEdit?.price || '',
    stock: productToEdit?.stock || ''
  });
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    setLoading(true);

    const payload = {
      name: formData.name,
      description: formData.description,
      price: parseFloat(formData.price),
      stock: parseInt(formData.stock, 10),
    };

    const action = isEditing 
      ? updateProduct(productToEdit.id, payload)
      : createProduct(payload);

    action
      .then(() => onClose(true))
      .catch(() => setError('No se pudo guardar el producto. Intente nuevamente.'))
      .finally(() => setLoading(false));
  };

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <div style={headerStyle}>
          <h2 style={{ margin: 0, fontSize: '1.2rem', color: NAVY }}>
            {isEditing ? 'Editar Producto' : 'Nuevo Producto'}
          </h2>
          <button onClick={() => onClose(false)} style={closeBtnStyle}>✕</button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: '24px' }}>
          {error && <p style={{ color: '#dc2626', fontSize: '0.85rem', marginBottom: '16px' }}>{error}</p>}
          
          <div style={{ marginBottom: '16px' }}>
            <label style={labelStyle}>Nombre</label>
            <input type="text" name="name" value={formData.name} onChange={handleChange} required style={inputStyle} />
          </div>

          <div style={{ marginBottom: '16px' }}>
            <label style={labelStyle}>Descripción</label>
            <textarea name="description" value={formData.description} onChange={handleChange} required style={{ ...inputStyle, minHeight: '80px', resize: 'vertical' }} />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px', marginBottom: '24px' }}>
            <div>
              <label style={labelStyle}>Precio ($)</label>
              <input type="number" step="0.01" name="price" value={formData.price} onChange={handleChange} required style={inputStyle} min="0" />
            </div>
            <div>
              <label style={labelStyle}>Stock inicial</label>
              <input type="number" name="stock" value={formData.stock} onChange={handleChange} required style={inputStyle} min="0" />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
            <button type="button" onClick={() => onClose(false)} style={btnCancelStyle}>Cancelar</button>
            <button type="submit" style={btnSubmitStyle} disabled={loading}>
              {loading ? 'Guardando...' : 'Guardar Producto'}
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

export default ProductForm;
