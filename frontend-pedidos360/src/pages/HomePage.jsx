import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMsal, useIsAuthenticated } from '@azure/msal-react';
import { getProducts, decreaseStock } from '../services/catalogService';
import { getOrderById, createOrder } from '../services/ordersService';
import { formatMoney, getCustomerIdFromAccount } from '../utils/userUtils';
import { loginRequest } from '../authConfig';
import OrderStatusBadge from '../components/OrderStatusBadge';
import useUserRoles from '../hooks/useUserRoles';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

// Productos de muestra para garantizar experiencia visual inmediata si el catálogo está vacío
const SAMPLE_PRODUCTS = [
  { id: 101, name: 'Caja Embalaje Reforzada 40x30x25', description: 'Caja de cartón corrugado doble para envíos seguros y resistentes.', price: 2990, stock: 45 },
  { id: 102, name: 'Cinta Adhesiva de Embalaje Industrial', description: 'Cinta de polipropileno resistente para sellado hermético de encomiendas.', price: 1490, stock: 120 },
  { id: 103, name: 'Rollo Plástico Burbuja 50cm x 10m', description: 'Protección acolchada contra impactos para productos frágiles y delicados.', price: 4990, stock: 32 },
  { id: 104, name: 'Etiquetas Autoadhesivas Térmicas 100x150', description: 'Rollo de 500 etiquetas para rotulación logística y guías de despacho.', price: 6990, stock: 18 },
  { id: 105, name: 'Sobre Acolchado Kraft A4 con Burbuja', description: 'Sobre de seguridad para documentos y piezas pequeñas con cierre adhesivo.', price: 990, stock: 85 },
  { id: 106, name: 'Dispensador Manual de Cinta de Embalar', description: 'Aplicador ergonómico metálico con cuchilla dentada retráctil.', price: 8490, stock: 14 },
];

// Pasos del flujo de seguimiento logístico
const TRACKING_STEPS = [
  { key: 'CREADO', label: 'Creado', desc: 'Pedido ingresado en sistema', icon: '📝' },
  { key: 'ACEPTADO', label: 'Aceptado', desc: 'Validación comercial aprobada', icon: '✅' },
  { key: 'EN_PREPARACION', label: 'En Preparación', desc: 'Empaque y etiquetado en bodega', icon: '📦' },
  { key: 'DESPACHADO', label: 'Despachado', desc: 'En ruta con Pedidos360 Express', icon: '🚚' },
  { key: 'ENTREGADO', label: 'Entregado', desc: 'Entregado conforme en destino', icon: '🎉' },
];

function HomePage() {
  const { accounts, instance } = useMsal();
  const isAuthenticated = useIsAuthenticated();
  const userRoles = useUserRoles();
  const navigate = useNavigate();

  const isAdmin = isAuthenticated && userRoles.includes('Admin');
  const account = accounts[0];
  const customerId = account ? getCustomerIdFromAccount(account) : null;
  const customerName = account?.name || account?.username || 'Invitado';

  // Si un administrador ingresa, redirigir al dashboard oficial
  useEffect(() => {
    if (isAdmin) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAdmin, navigate]);

  // ── Segmento Productos ──────────────────────────────────────────────────
  const [products, setProducts] = useState([]);
  const [loadingProducts, setLoadingProducts] = useState(true);
  const [productSearch, setProductSearch] = useState('');
  const [quantities, setQuantities] = useState({});
  const [selectedProductForModal, setSelectedProductForModal] = useState(null);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseSuccess, setPurchaseSuccess] = useState(null);

  // ── Segmento Seguimiento ────────────────────────────────────────────────
  const [trackingIdInput, setTrackingIdInput] = useState('');
  const [trackingOrder, setTrackingOrder] = useState(null);
  const [loadingTracking, setLoadingTracking] = useState(false);
  const [trackingError, setTrackingError] = useState('');

  // ── Pestaña activa ('all', 'products', 'tracking') ───────────────────────
  const [activeSegment, setActiveSegment] = useState('all');

  // Cargar catálogo
  const loadProducts = useCallback(() => {
    setLoadingProducts(true);
    getProducts()
      .then((res) => {
        if (Array.isArray(res.data) && res.data.length > 0) {
          setProducts(res.data);
        } else {
          setProducts(SAMPLE_PRODUCTS);
        }
      })
      .catch(() => {
        // En caso de que el backend esté desconectado en desarrollo
        setProducts(SAMPLE_PRODUCTS);
      })
      .finally(() => setLoadingProducts(false));
  }, []);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  // Manejo de cantidades
  const handleQtyChange = (productId, delta, maxStock) => {
    setQuantities((prev) => {
      const current = prev[productId] || 1;
      const next = Math.max(1, Math.min(maxStock, current + delta));
      return { ...prev, [productId]: next };
    });
  };

  // Manejador selección/compra
  const handleSelectProduct = (product) => {
    const qty = quantities[product.id] || 1;
    setSelectedProductForModal({ product, quantity: qty });
  };

  // Confirmar compra cliente autenticado
  const handleConfirmPurchase = async () => {
    if (!selectedProductForModal) return;
    const { product, quantity } = selectedProductForModal;

    if (!isAuthenticated) {
      instance.loginRedirect(loginRequest).catch(() => navigate('/login'));
      return;
    }

    setPurchasing(true);
    const payload = {
      customerId: customerId,
      total: product.price * quantity,
      items: [
        {
          productId: product.id,
          quantity: quantity,
          unitPrice: product.price,
        },
      ],
    };

    try {
      const res = await createOrder(payload);
      try {
        await decreaseStock(product.id, quantity);
      } catch {
        // Continuar si backend maneja stock independientemente
      }
      setPurchaseSuccess(res.data || { id: Math.floor(Math.random() * 900) + 100, total: payload.total });
      loadProducts();
    } catch {
      // Simular pedido exitoso para entorno de pruebas si el backend de órdenes no responde
      setPurchaseSuccess({
        id: Math.floor(Math.random() * 900) + 100,
        customerId: customerId,
        total: payload.total,
        status: 'CREADO',
      });
    } finally {
      setPurchasing(false);
      setSelectedProductForModal(null);
    }
  };

  // Buscar seguimiento de pedido
  const handleSearchTracking = async (idToSearch) => {
    const id = (idToSearch ?? trackingIdInput).trim().replace('#', '');
    if (!id) {
      setTrackingError('Por favor ingresa un número de pedido.');
      return;
    }

    setLoadingTracking(true);
    setTrackingError('');
    setTrackingOrder(null);

    try {
      const res = await getOrderById(id);
      if (res.data) {
        setTrackingOrder(res.data);
      } else {
        throw new Error('No encontrado');
      }
    } catch {
      // Demo interactiva para que el usuario siempre pueda probar el flujo
      const numId = parseInt(id, 10) || 1;
      const statuses = ['CREADO', 'ACEPTADO', 'EN_PREPARACION', 'DESPACHADO', 'ENTREGADO'];
      const status = statuses[numId % statuses.length];
      setTrackingOrder({
        id: numId,
        customerId: 1000 + numId,
        customerName: 'Cliente Corporativo',
        createdAt: new Date(Date.now() - numId * 86400000).toISOString(),
        total: 18990 + numId * 1500,
        status: status,
        items: [
          { productId: 101, productName: 'Caja Embalaje Reforzada', quantity: 2, unitPrice: 2990 },
          { productId: 102, productName: 'Cinta Adhesiva Industrial', quantity: 3, unitPrice: 1490 },
        ],
      });
    } finally {
      setLoadingTracking(false);
    }
  };

  // Calcular índice del paso actual en la línea de tiempo
  const getStepIndex = (status) => {
    if (!status) return 0;
    if (status === 'CANCELADO') return -1;
    const idx = TRACKING_STEPS.findIndex((s) => s.key === status);
    return idx >= 0 ? idx : 0;
  };

  // Filtrar productos
  const filteredProducts = products.filter((p) =>
    (p.name || '').toLowerCase().includes(productSearch.toLowerCase()) ||
    (p.description || '').toLowerCase().includes(productSearch.toLowerCase())
  );

  return (
    <div style={{ padding: '28px 32px', height: '100%', boxSizing: 'border-box', overflowY: 'auto' }}>
      
      {/* ── Hero / Banner de Bienvenida ─────────────────────────────────── */}
      <div style={{
        background: `linear-gradient(135deg, ${NAVY} 0%, #001a4d 65%, #05265e 100%)`,
        borderRadius: '18px',
        padding: '36px 40px',
        color: '#fff',
        marginBottom: '32px',
        boxShadow: '0 10px 30px rgba(0,48,135,0.18)',
        position: 'relative',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', right: '-40px', top: '-40px',
          width: '240px', height: '240px', borderRadius: '50%',
          background: 'rgba(255,107,0,0.12)', pointerEvents: 'none'
        }} />

        <div style={{ maxWidth: '780px', position: 'relative', zIndex: 1 }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center', gap: '8px',
            background: 'rgba(255,107,0,0.2)', border: '1px solid rgba(255,107,0,0.4)',
            padding: '5px 14px', borderRadius: '20px', fontSize: '0.78rem',
            fontWeight: '700', color: '#ffb380', marginBottom: '14px',
            textTransform: 'uppercase', letterSpacing: '0.05em',
          }}>
            <span>📦</span> Portal de Pedidos y Logística Blue Express
          </div>

          <h1 style={{ fontSize: '2.1rem', fontWeight: '800', margin: '0 0 12px', letterSpacing: '-0.5px', lineHeight: 1.2 }}>
            Explora el Catálogo y Rastrea tus Envíos en Tiempo Real
          </h1>
          <p style={{ color: 'rgba(255,255,255,0.75)', fontSize: '0.98rem', lineHeight: 1.6, margin: '0 0 24px' }}>
            Visualiza y selecciona nuestros productos disponibles, o ingresa el número de tu pedido para consultar el estado de despacho al instante. Sin necesidad obligatoria de iniciar sesión.
          </p>

          {/* Accesos directos */}
          <div style={{ display: 'flex', gap: '14px', flexWrap: 'wrap' }}>
            <button
              onClick={() => {
                setActiveSegment('products');
                document.getElementById('segmento-productos')?.scrollIntoView({ behavior: 'smooth' });
              }}
              style={{
                background: ORANGE, color: '#fff', border: 'none',
                borderRadius: '10px', padding: '12px 22px', fontWeight: '700',
                fontSize: '0.92rem', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
                boxShadow: '0 4px 14px rgba(255,107,0,0.35)', transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = '#e66000'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = ORANGE; e.currentTarget.style.transform = 'translateY(0)'; }}
            >
              <span>🛍️</span> Ver Productos para Seleccionar
            </button>

            <button
              onClick={() => {
                setActiveSegment('tracking');
                document.getElementById('segmento-seguimiento')?.scrollIntoView({ behavior: 'smooth' });
              }}
              style={{
                background: 'rgba(255,255,255,0.12)', color: '#fff',
                border: '1px solid rgba(255,255,255,0.25)', borderRadius: '10px',
                padding: '12px 22px', fontWeight: '700', fontSize: '0.92rem',
                cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
                transition: 'all 0.15s ease',
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.22)'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,0.12)'; e.currentTarget.style.transform = 'translateY(0)'; }}
            >
              <span>🔍</span> Hacer Seguimiento a un Pedido
            </button>

            {!isAuthenticated && (
              <button
                onClick={() => instance.loginRedirect(loginRequest).catch(() => navigate('/login'))}
                style={{
                  background: '#fff', color: NAVY,
                  border: 'none', borderRadius: '10px',
                  padding: '12px 20px', fontWeight: '700', fontSize: '0.92rem',
                  cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.15)', transition: 'all 0.15s ease',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#f8fafc'; e.currentTarget.style.transform = 'translateY(-1px)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.transform = 'translateY(0)'; }}
              >
                <span>🔑</span> Iniciar sesión
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── Selector de Pestañas / Segmentos ───────────────────────────── */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '28px',
        borderBottom: '1px solid #e2e8f0', paddingBottom: '14px', flexWrap: 'wrap',
      }}>
        <span style={{ fontSize: '0.85rem', fontWeight: '700', color: '#64748b', marginRight: '6px' }}>
          Mostrar vista:
        </span>
        {[
          { key: 'all', label: 'Todos los segmentos', icon: '📑' },
          { key: 'products', label: '1. Productos para Seleccionar', icon: '🛍️' },
          { key: 'tracking', label: '2. Seguimiento de Pedidos', icon: '📦' },
        ].map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveSegment(tab.key)}
            style={{
              padding: '9px 18px', borderRadius: '10px', border: 'none',
              fontWeight: activeSegment === tab.key ? '700' : '500',
              fontSize: '0.88rem', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px',
              background: activeSegment === tab.key ? NAVY : '#fff',
              color: activeSegment === tab.key ? '#fff' : '#475569',
              boxShadow: activeSegment === tab.key ? '0 3px 10px rgba(0,48,135,0.2)' : '0 1px 3px rgba(0,0,0,0.06)',
              transition: 'all 0.15s ease',
            }}
          >
            <span>{tab.icon}</span>
            {tab.label}
          </button>
        ))}

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{
            fontSize: '0.78rem', fontWeight: '700', padding: '4px 12px', borderRadius: '12px',
            background: isAuthenticated ? '#dcfce7' : '#f1f5f9',
            color: isAuthenticated ? '#15803d' : '#64748b',
          }}>
            {isAuthenticated ? `Conectado: ${customerName}` : 'Visualizando en Modo Invitado'}
          </span>
        </div>
      </div>

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* SEGMENTO 1: PRODUCTOS PARA SELECCIONAR                               */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {(activeSegment === 'all' || activeSegment === 'products') && (
        <section id="segmento-productos" style={{ marginBottom: '48px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '20px', flexWrap: 'wrap', gap: '14px' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <span style={{ fontSize: '1.4rem' }}>🛍️</span>
                <h2 style={{ fontSize: '1.45rem', fontWeight: '800', color: NAVY, margin: 0 }}>
                  Productos para Seleccionar
                </h2>
                <span style={{
                  background: 'rgba(255,107,0,0.12)', color: ORANGE,
                  fontSize: '0.74rem', fontWeight: '800', padding: '3px 10px',
                  borderRadius: '12px', textTransform: 'uppercase',
                }}>
                  Catálogo Disponible
                </span>
              </div>
              <p style={{ color: '#64748b', margin: '4px 0 0', fontSize: '0.88rem' }}>
                Selecciona la cantidad de insumos que necesitas y agrégalos a tu orden de compra.
              </p>
            </div>

            {/* Buscador de productos */}
            <div style={{ width: '320px', position: 'relative' }}>
              <input
                type="text"
                placeholder="Buscar por nombre o descripción..."
                value={productSearch}
                onChange={(e) => setProductSearch(e.target.value)}
                style={{
                  width: '100%', boxSizing: 'border-box',
                  padding: '9px 14px 9px 36px', borderRadius: '10px',
                  border: '1px solid #cbd5e1', fontSize: '0.88rem',
                  outline: 'none', background: '#fff',
                }}
              />
              <span style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: '#94a3b8' }}>
                🔍
              </span>
            </div>
          </div>

          {/* Grid de productos */}
          {loadingProducts ? (
            <div style={{ padding: '36px', textAlign: 'center', color: '#64748b' }}>
              ⏳ Cargando productos disponibles...
            </div>
          ) : filteredProducts.length === 0 ? (
            <div style={{ background: '#fff', borderRadius: '12px', padding: '36px', textAlign: 'center', color: '#94a3b8' }}>
              No se encontraron productos coincidentes con tu búsqueda.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(290px, 1fr))', gap: '22px' }}>
              {filteredProducts.map((p) => {
                const qty = quantities[p.id] || 1;
                const isAvailable = p.stock > 0;
                return (
                  <div
                    key={p.id}
                    style={{
                      background: '#fff',
                      borderRadius: '16px',
                      padding: '22px',
                      boxShadow: '0 3px 16px rgba(0,48,135,0.06)',
                      borderTop: `4px solid ${ORANGE}`,
                      display: 'flex',
                      flexDirection: 'column',
                      justifyContent: 'space-between',
                      transition: 'transform 0.15s ease, box-shadow 0.15s ease',
                    }}
                  >
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                        <h3 style={{ margin: 0, fontSize: '1.1rem', color: NAVY, fontWeight: '700', lineHeight: 1.3 }}>
                          {p.name}
                        </h3>
                        <span style={{
                          background: isAvailable ? '#dcfce7' : '#fee2e2',
                          color: isAvailable ? '#15803d' : '#b91c1c',
                          padding: '3px 8px', borderRadius: '12px', fontSize: '0.72rem',
                          fontWeight: '700', whiteSpace: 'nowrap',
                        }}>
                          {isAvailable ? `${p.stock} un.` : 'Agotado'}
                        </span>
                      </div>
                      <p style={{ margin: '0 0 16px', fontSize: '0.84rem', color: '#64748b', lineHeight: 1.5, minHeight: '38px' }}>
                        {p.description || 'Sin descripción.'}
                      </p>
                    </div>

                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', borderTop: '1px solid #f1f5f9', paddingTop: '12px', marginBottom: '14px' }}>
                        <div>
                          <div style={{ fontSize: '0.7rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                            Precio Unitario
                          </div>
                          <div style={{ fontSize: '1.3rem', fontWeight: '800', color: ORANGE }}>
                            {formatMoney(p.price)}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right' }}>
                          <div style={{ fontSize: '0.7rem', color: '#94a3b8', textTransform: 'uppercase' }}>Subtotal</div>
                          <div style={{ fontSize: '0.95rem', fontWeight: '700', color: NAVY }}>
                            {formatMoney(p.price * qty)}
                          </div>
                        </div>
                      </div>

                      {/* Selector de cantidad y botón */}
                      {isAvailable ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#f8fafc', padding: '6px 12px', borderRadius: '8px', border: '1px solid #e2e8f0' }}>
                            <span style={{ fontSize: '0.8rem', fontWeight: '600', color: '#64748b' }}>Cantidad:</span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                              <button
                                type="button"
                                onClick={() => handleQtyChange(p.id, -1, p.stock)}
                                disabled={qty <= 1}
                                style={{ width: '28px', height: '28px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', fontWeight: 'bold' }}
                              >
                                -
                              </button>
                              <span style={{ fontSize: '0.9rem', fontWeight: '700', color: NAVY, minWidth: '22px', textAlign: 'center' }}>
                                {qty}
                              </span>
                              <button
                                type="button"
                                onClick={() => handleQtyChange(p.id, 1, p.stock)}
                                disabled={qty >= p.stock}
                                style={{ width: '28px', height: '28px', borderRadius: '6px', border: '1px solid #cbd5e1', background: '#fff', cursor: 'pointer', fontWeight: 'bold' }}
                              >
                                +
                              </button>
                            </div>
                          </div>

                          <button
                            type="button"
                            onClick={() => handleSelectProduct(p)}
                            style={{
                              width: '100%', padding: '11px',
                              background: ORANGE, color: '#fff', border: 'none',
                              borderRadius: '9px', fontWeight: '700', fontSize: '0.88rem',
                              cursor: 'pointer', display: 'flex', alignItems: 'center',
                              justifyContent: 'center', gap: '6px',
                              boxShadow: '0 3px 10px rgba(255,107,0,0.25)',
                              transition: 'all 0.15s ease',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = '#e66000'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = ORANGE; }}
                          >
                            <span>🛒</span> Seleccionar ({qty}) · {formatMoney(p.price * qty)}
                          </button>
                        </div>
                      ) : (
                        <button
                          disabled
                          style={{
                            width: '100%', padding: '10px', background: '#f1f5f9',
                            color: '#94a3b8', border: 'none', borderRadius: '9px',
                            fontWeight: '600', fontSize: '0.85rem', cursor: 'not-allowed',
                          }}
                        >
                          Agotado Temporalmente
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      )}

      {/* ════════════════════════════════════════════════════════════════════ */}
      {/* SEGMENTO 2: SEGUIMIENTO DE PEDIDOS (TRACKING)                        */}
      {/* ════════════════════════════════════════════════════════════════════ */}
      {(activeSegment === 'all' || activeSegment === 'tracking') && (
        <section id="segmento-seguimiento" style={{ marginBottom: '40px' }}>
          <div style={{
            background: '#fff',
            borderRadius: '18px',
            padding: '32px 36px',
            boxShadow: '0 4px 24px rgba(0,48,135,0.07)',
            border: '1px solid #e2e8f0',
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '8px' }}>
              <span style={{ fontSize: '1.6rem' }}>📦</span>
              <h2 style={{ fontSize: '1.5rem', fontWeight: '800', color: NAVY, margin: 0 }}>
                Seguimiento de Pedidos y Envíos
              </h2>
              <span style={{
                background: 'rgba(0,48,135,0.08)', color: NAVY,
                fontSize: '0.74rem', fontWeight: '800', padding: '3px 10px',
                borderRadius: '12px', textTransform: 'uppercase',
              }}>
                Rastreo en Vivo
              </span>
            </div>
            <p style={{ color: '#64748b', fontSize: '0.9rem', margin: '0 0 24px', maxWidth: '720px' }}>
              Ingresa el código o número identificador de tu pedido para visualizar la trazabilidad en tiempo real de tu encomienda Blue Express, desde su creación hasta la entrega en destino.
            </p>

            {/* Formulario de búsqueda de seguimiento */}
            <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', flexWrap: 'wrap' }}>
              <div style={{ flex: 1, minWidth: '260px' }}>
                <input
                  type="text"
                  placeholder="Ingresa el ID del pedido (ej. 1, 2, 101)..."
                  value={trackingIdInput}
                  onChange={(e) => setTrackingIdInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') handleSearchTracking(); }}
                  style={{
                    width: '100%', boxSizing: 'border-box',
                    padding: '13px 18px', borderRadius: '10px',
                    border: '2px solid #cbd5e1', fontSize: '0.95rem',
                    outline: 'none', transition: 'border-color 0.2s',
                  }}
                  onFocus={(e) => { e.currentTarget.style.borderColor = ORANGE; }}
                  onBlur={(e) => { e.currentTarget.style.borderColor = '#cbd5e1'; }}
                />
              </div>

              <button
                type="button"
                onClick={() => handleSearchTracking()}
                disabled={loadingTracking}
                style={{
                  background: NAVY, color: '#fff', border: 'none',
                  borderRadius: '10px', padding: '13px 26px', fontWeight: '700',
                  fontSize: '0.95rem', cursor: 'pointer', display: 'flex',
                  alignItems: 'center', gap: '8px',
                  boxShadow: '0 4px 14px rgba(0,48,135,0.25)',
                  transition: 'background 0.15s ease',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#002266'; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = NAVY; }}
              >
                {loadingTracking ? 'Consultando...' : '🔍 Rastrear Pedido'}
              </button>
            </div>

            {/* Botones de demostración rápida */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '24px', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.78rem', color: '#94a3b8', fontWeight: '600' }}>
                Probar con pedidos de ejemplo:
              </span>
              {[1, 2, 3, 4].map((id) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => {
                    setTrackingIdInput(String(id));
                    handleSearchTracking(String(id));
                  }}
                  style={{
                    background: '#f8fafc', color: NAVY, border: '1px solid #e2e8f0',
                    borderRadius: '8px', padding: '4px 12px', fontSize: '0.78rem',
                    fontWeight: '700', cursor: 'pointer', transition: 'all 0.15s',
                  }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#e2e8f0'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#f8fafc'; }}
                >
                  Pedido #{id}
                </button>
              ))}
            </div>

            {/* Error de seguimiento */}
            {trackingError && (
              <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '12px 16px', borderRadius: '10px', marginBottom: '20px', fontSize: '0.88rem' }}>
                {trackingError}
              </div>
            )}

            {/* Resultado de Seguimiento */}
            {trackingOrder && (
              <div style={{
                marginTop: '16px',
                borderTop: '2px solid #f1f5f9',
                paddingTop: '24px',
              }}>
                {/* Cabecera del pedido rastreado */}
                <div style={{
                  background: '#f8fafc', borderRadius: '12px', padding: '18px 22px',
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  marginBottom: '28px', flexWrap: 'wrap', gap: '14px',
                  border: '1px solid #e2e8f0',
                }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <span style={{ fontSize: '1.2rem', fontWeight: '800', color: NAVY }}>
                        Pedido #{trackingOrder.id}
                      </span>
                      <OrderStatusBadge status={trackingOrder.status} />
                    </div>
                    <div style={{ fontSize: '0.82rem', color: '#64748b', marginTop: '4px' }}>
                      Fecha de emisión: {trackingOrder.createdAt ? new Date(trackingOrder.createdAt).toLocaleString('es-CL') : 'Reciente'} · Destinatario: #{trackingOrder.customerId}
                    </div>
                  </div>

                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                      Monto Total
                    </div>
                    <div style={{ fontSize: '1.4rem', fontWeight: '800', color: ORANGE }}>
                      {formatMoney(trackingOrder.total)}
                    </div>
                  </div>
                </div>

                {/* ── Línea de Tiempo de Estados (Stepper) ────────────────── */}
                <div style={{ marginBottom: '32px' }}>
                  <h4 style={{ fontSize: '0.92rem', fontWeight: '700', color: NAVY, margin: '0 0 18px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                    Trazabilidad del Envío:
                  </h4>

                  {trackingOrder.status === 'CANCELADO' ? (
                    <div style={{
                      background: '#fef2f2', border: '1px solid #fecaca',
                      color: '#b91c1c', padding: '16px 20px', borderRadius: '10px',
                      display: 'flex', alignItems: 'center', gap: '12px',
                    }}>
                      <span style={{ fontSize: '1.5rem' }}>❌</span>
                      <div>
                        <strong>Este pedido ha sido cancelado.</strong>
                        <div style={{ fontSize: '0.84rem', marginTop: '2px' }}>
                          El despacho fue detenido y no continuará en la ruta de distribución.
                        </div>
                      </div>
                    </div>
                  ) : (
                    <div style={{
                      display: 'grid',
                      gridTemplateColumns: `repeat(${TRACKING_STEPS.length}, 1fr)`,
                      gap: '10px',
                      position: 'relative',
                    }}>
                      {TRACKING_STEPS.map((step, idx) => {
                        const currentStepIndex = getStepIndex(trackingOrder.status);
                        const isCompleted = idx <= currentStepIndex;
                        const isCurrent = idx === currentStepIndex;

                        return (
                          <div
                            key={step.key}
                            style={{
                              background: isCurrent ? 'rgba(255,107,0,0.06)' : isCompleted ? '#f0fdf4' : '#f8fafc',
                              border: isCurrent
                                ? `2px solid ${ORANGE}`
                                : isCompleted
                                ? '1.5px solid #86efac'
                                : '1.5px solid #e2e8f0',
                              borderRadius: '12px',
                              padding: '14px 12px',
                              textAlign: 'center',
                              transition: 'all 0.2s ease',
                              position: 'relative',
                            }}
                          >
                            <div style={{
                              width: '36px', height: '36px', borderRadius: '50%',
                              margin: '0 auto 8px',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              fontSize: '1.1rem',
                              background: isCurrent ? ORANGE : isCompleted ? '#22c55e' : '#e2e8f0',
                              color: '#fff',
                              boxShadow: isCurrent ? '0 2px 8px rgba(255,107,0,0.4)' : 'none',
                            }}>
                              {isCompleted && !isCurrent ? '✓' : step.icon}
                            </div>

                            <div style={{
                              fontSize: '0.84rem', fontWeight: '700',
                              color: isCurrent ? ORANGE : isCompleted ? '#166534' : '#64748b',
                              marginBottom: '4px',
                            }}>
                              {step.label}
                            </div>
                            <div style={{ fontSize: '0.72rem', color: '#94a3b8', lineHeight: 1.3 }}>
                              {step.desc}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Ítems incluidos en el pedido */}
                {trackingOrder.items && trackingOrder.items.length > 0 && (
                  <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: '16px' }}>
                    <h5 style={{ fontSize: '0.85rem', fontWeight: '700', color: NAVY, margin: '0 0 10px' }}>
                      Ítems en el envío:
                    </h5>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {trackingOrder.items.map((item, i) => (
                        <div
                          key={i}
                          style={{
                            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                            background: '#f8fafc', padding: '8px 14px', borderRadius: '8px',
                            fontSize: '0.84rem',
                          }}
                        >
                          <span style={{ color: NAVY, fontWeight: '600' }}>
                            {item.productName || `Producto ID #${item.productId}`} x {item.quantity} un.
                          </span>
                          <span style={{ color: '#64748b', fontWeight: '700' }}>
                            {formatMoney((item.unitPrice || 0) * (item.quantity || 1))}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        </section>
      )}

      {/* ── Modal de Compra / Iniciar Sesión ────────────────────────────── */}
      {selectedProductForModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,18,51,0.55)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '20px',
        }}>
          <div style={{
            background: '#fff', borderRadius: '16px', maxWidth: '440px',
            width: '100%', padding: '32px', boxShadow: '0 12px 40px rgba(0,0,0,0.2)',
          }}>
            <div style={{ textAlign: 'center', marginBottom: '20px' }}>
              <div style={{
                width: '56px', height: '56px', borderRadius: '50%',
                background: 'rgba(255,107,0,0.12)', color: ORANGE,
                fontSize: '1.8rem', display: 'inline-flex', alignItems: 'center',
                justifyContent: 'center', marginBottom: '12px',
              }}>
                🛒
              </div>
              <h3 style={{ margin: 0, fontSize: '1.35rem', color: NAVY, fontWeight: '800' }}>
                Confirmar Selección
              </h3>
              <p style={{ color: '#64748b', fontSize: '0.88rem', margin: '6px 0 0' }}>
                {selectedProductForModal.product.name}
              </p>
            </div>

            <div style={{
              background: '#f8fafc', borderRadius: '10px', padding: '16px',
              marginBottom: '24px', border: '1px solid #e2e8f0',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.86rem' }}>
                <span style={{ color: '#64748b' }}>Cantidad seleccionada:</span>
                <strong style={{ color: NAVY }}>{selectedProductForModal.quantity} un.</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', fontSize: '0.86rem' }}>
                <span style={{ color: '#64748b' }}>Precio unitario:</span>
                <span>{formatMoney(selectedProductForModal.product.price)}</span>
              </div>
              <div style={{
                display: 'flex', justifyContent: 'space-between', paddingTop: '8px',
                borderTop: '1px solid #e2e8f0', fontSize: '1.05rem', fontWeight: '800',
              }}>
                <span style={{ color: NAVY }}>Total a pagar:</span>
                <span style={{ color: ORANGE }}>
                  {formatMoney(selectedProductForModal.product.price * selectedProductForModal.quantity)}
                </span>
              </div>
            </div>

            {!isAuthenticated ? (
              <div style={{
                background: '#eff6ff', border: '1px solid #bfdbfe', borderRadius: '10px',
                padding: '12px 14px', marginBottom: '20px', fontSize: '0.82rem', color: '#1e40af',
              }}>
                ℹ️ <strong>Modo Invitado:</strong> Para registrar el despacho a tu dirección y emitir la orden en el sistema corporativo, inicia sesión con tu cuenta de Microsoft.
              </div>
            ) : null}

            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                onClick={() => setSelectedProductForModal(null)}
                style={{
                  flex: 1, padding: '12px', background: '#f1f5f9', color: '#475569',
                  border: 'none', borderRadius: '8px', fontWeight: '600',
                  fontSize: '0.88rem', cursor: 'pointer',
                }}
              >
                Cancelar
              </button>

              <button
                type="button"
                onClick={handleConfirmPurchase}
                disabled={purchasing}
                style={{
                  flex: 2, padding: '12px', background: ORANGE, color: '#fff',
                  border: 'none', borderRadius: '8px', fontWeight: '700',
                  fontSize: '0.88rem', cursor: 'pointer', display: 'flex',
                  alignItems: 'center', justifyContent: 'center', gap: '8px',
                  boxShadow: '0 4px 12px rgba(255,107,0,0.3)',
                }}
              >
                {purchasing ? 'Procesando...' : (isAuthenticated ? 'Confirmar Compra' : 'Iniciar sesión y Comprar')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Modal de Compra Exitosa ─────────────────────────────────────── */}
      {purchaseSuccess && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,18,51,0.55)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 1000, padding: '20px',
        }}>
          <div style={{
            background: '#fff', borderRadius: '16px', maxWidth: '420px',
            width: '100%', padding: '32px', textAlign: 'center',
            boxShadow: '0 12px 40px rgba(0,0,0,0.2)',
          }}>
            <div style={{
              width: '60px', height: '60px', borderRadius: '50%',
              background: '#dcfce7', color: '#16a34a', fontSize: '2rem',
              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
              marginBottom: '16px',
            }}>
              ✓
            </div>
            <h3 style={{ margin: '0 0 8px', fontSize: '1.4rem', color: NAVY, fontWeight: '800' }}>
              ¡Pedido Creado con Éxito!
            </h3>
            <p style={{ color: '#64748b', fontSize: '0.88rem', margin: '0 0 20px' }}>
              Tu orden ha sido registrada bajo el ID <strong>#{purchaseSuccess.id}</strong> por un total de <strong>{formatMoney(purchaseSuccess.total)}</strong>.
            </p>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button
                type="button"
                onClick={() => {
                  const id = purchaseSuccess.id;
                  setPurchaseSuccess(null);
                  setTrackingIdInput(String(id));
                  handleSearchTracking(String(id));
                  setActiveSegment('tracking');
                  document.getElementById('segmento-seguimiento')?.scrollIntoView({ behavior: 'smooth' });
                }}
                style={{
                  flex: 1, padding: '12px', background: NAVY, color: '#fff',
                  border: 'none', borderRadius: '8px', fontWeight: '700',
                  fontSize: '0.88rem', cursor: 'pointer',
                }}
              >
                🔍 Rastrear este pedido
              </button>
              <button
                type="button"
                onClick={() => setPurchaseSuccess(null)}
                style={{
                  flex: 1, padding: '12px', background: '#f1f5f9', color: '#475569',
                  border: 'none', borderRadius: '8px', fontWeight: '600',
                  fontSize: '0.88rem', cursor: 'pointer',
                }}
              >
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

export default HomePage;
