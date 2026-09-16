import { useEffect, useState, useMemo } from 'react';
import { getOrders } from '../services/ordersService';
import { getProducts } from '../services/catalogService';
import { formatMoney } from '../utils/userUtils';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const STATUS_LABELS = {
  CREADO: 'Creado',
  ACEPTADO: 'Aceptado',
  EN_PREPARACION: 'En preparación',
  DESPACHADO: 'Despachado',
  ENTREGADO: 'Entregado',
  CANCELADO: 'Cancelado',
};

const STATUS_COLORS = {
  CREADO: '#3b82f6',
  ACEPTADO: '#8b5cf6',
  EN_PREPARACION: '#f59e0b',
  DESPACHADO: '#f97316',
  ENTREGADO: '#10b981',
  CANCELADO: '#ef4444',
};

function ReportsPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [orders, setOrders] = useState([]);
  const [products, setProducts] = useState([]);
  const [statusFilter, setStatusFilter] = useState('ACTIVE'); // 'ACTIVE', 'DELIVERED', 'ALL'

  const loadData = () => {
    setLoading(true);
    setError('');
    Promise.all([getOrders(), getProducts()])
      .then(([ordersRes, productsRes]) => {
        setOrders(Array.isArray(ordersRes.data) ? ordersRes.data : []);
        setProducts(Array.isArray(productsRes.data) ? productsRes.data : []);
      })
      .catch((err) => {
        console.error('Error cargando datos de reportes:', err);
        setError('No se pudieron obtener las métricas del servidor. Asegúrese de que el backend esté en ejecución.');
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadData();
  }, []);

  // Filtrado de pedidos según criterio seleccionado
  const filteredOrders = useMemo(() => {
    if (statusFilter === 'DELIVERED') {
      return orders.filter(o => o.status === 'ENTREGADO');
    }
    if (statusFilter === 'ACTIVE') {
      return orders.filter(o => o.status !== 'CANCELADO');
    }
    return orders;
  }, [orders, statusFilter]);

  // Cálculos de métricas globales
  const stats = useMemo(() => {
    const totalRevenue = filteredOrders.reduce((sum, o) => sum + (Number(o.total) || 0), 0);
    const totalOrders = filteredOrders.length;
    const avgTicket = totalOrders > 0 ? totalRevenue / totalOrders : 0;

    let totalUnitsSold = 0;
    filteredOrders.forEach(o => {
      if (Array.isArray(o.items)) {
        o.items.forEach(i => {
          totalUnitsSold += (Number(i.quantity) || 0);
        });
      }
    });

    return { totalRevenue, totalOrders, avgTicket, totalUnitsSold };
  }, [filteredOrders]);

  // Gráfico de Ingresos Mensuales
  const monthlyChartData = useMemo(() => {
    const monthlyRevenue = Array(12).fill(0);
    filteredOrders.forEach(o => {
      const d = new Date(o.createdAt);
      if (!isNaN(d.getTime())) {
        const m = d.getMonth();
        monthlyRevenue[m] += (Number(o.total) || 0);
      }
    });

    const currentMonth = new Date().getMonth();
    const startMonth = Math.max(0, currentMonth - 5);
    let maxRev = Math.max(...monthlyRevenue.slice(startMonth, currentMonth + 1));
    if (maxRev === 0) maxRev = 1;

    const data = [];
    for (let i = startMonth; i <= currentMonth; i++) {
      data.push({
        month: MONTHS[i],
        percentage: Math.round((monthlyRevenue[i] / maxRev) * 100),
        realValue: monthlyRevenue[i],
      });
    }
    return data;
  }, [filteredOrders]);

  // Productos Más Vendidos
  const topProducts = useMemo(() => {
    const statsByProduct = {};

    filteredOrders.forEach(o => {
      if (Array.isArray(o.items)) {
        o.items.forEach(item => {
          const pid = item.productId;
          if (!statsByProduct[pid]) {
            statsByProduct[pid] = { units: 0, revenue: 0 };
          }
          const qty = Number(item.quantity) || 0;
          const price = Number(item.unitPrice) || 0;
          statsByProduct[pid].units += qty;
          statsByProduct[pid].revenue += qty * price;
        });
      }
    });

    return Object.keys(statsByProduct)
      .map(pid => {
        const product = products.find(p => p.id === parseInt(pid, 10));
        return {
          id: pid,
          name: product ? product.name : `Producto #${pid}`,
          units: statsByProduct[pid].units,
          revenue: statsByProduct[pid].revenue,
        };
      })
      .sort((a, b) => b.units - a.units)
      .slice(0, 5);
  }, [filteredOrders, products]);

  // Distribución por estado de todos los pedidos
  const statusDistribution = useMemo(() => {
    const counts = {
      CREADO: 0,
      ACEPTADO: 0,
      EN_PREPARACION: 0,
      DESPACHADO: 0,
      ENTREGADO: 0,
      CANCELADO: 0,
    };
    orders.forEach(o => {
      if (counts[o.status] !== undefined) {
        counts[o.status]++;
      }
    });
    const total = orders.length || 1;
    return Object.keys(counts).map(st => ({
      status: st,
      label: STATUS_LABELS[st] || st,
      count: counts[st],
      color: STATUS_COLORS[st] || '#64748b',
      percentage: Math.round((counts[st] / total) * 100),
    }));
  }, [orders]);

  if (loading) {
    return (
      <div style={{ padding: '48px 36px', color: '#64748b', display: 'flex', alignItems: 'center', gap: '12px' }}>
        <span style={{ fontSize: '1.5rem' }}>⏳</span>
        <span style={{ fontSize: '1rem', fontWeight: '500' }}>Calculando reportes financieros y operativos...</span>
      </div>
    );
  }

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box', overflowY: 'auto' }}>
      
      {/* ── Encabezado y Filtros ────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '28px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: '0 0 4px' }}>
            Reportes Financieros y Operativos
          </h1>
          <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>
            Métricas de ingresos, ventas por producto y estado de pedidos.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <label style={{ fontSize: '0.85rem', fontWeight: '600', color: NAVY }}>Criterio:</label>
          <select 
            value={statusFilter} 
            onChange={e => setStatusFilter(e.target.value)}
            style={{
              padding: '8px 14px', borderRadius: '8px', border: '1px solid #cbd5e1',
              fontSize: '0.85rem', color: NAVY, background: '#fff', fontWeight: '600', cursor: 'pointer',
            }}
          >
            <option value="ACTIVE">Pedidos válidos (sin cancelados)</option>
            <option value="DELIVERED">Solo pedidos entregados</option>
            <option value="ALL">Todos los pedidos (incluye cancelados)</option>
          </select>

          <button 
            onClick={loadData}
            style={{
              padding: '8px 14px', borderRadius: '8px', border: 'none',
              background: NAVY, color: '#fff', fontSize: '0.85rem', fontWeight: '600',
              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '6px',
            }}
          >
            🔄 Actualizar
          </button>
        </div>
      </div>

      {error && (
        <div style={{ background: '#fee2e2', color: '#b91c1c', padding: '14px 18px', borderRadius: '10px', marginBottom: '24px', fontSize: '0.88rem' }}>
          ⚠️ {error}
        </div>
      )}

      {/* ── Tarjetas KPI ──────────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px', marginBottom: '28px' }}>
        <div style={kpiCardStyle}>
          <div style={{ fontSize: '1.3rem', marginBottom: '6px' }}>💰</div>
          <div style={{ fontSize: '1.6rem', fontWeight: '800', color: NAVY }}>
            {formatMoney(stats.totalRevenue)}
          </div>
          <div style={kpiLabelStyle}>Total Facturado</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '1.3rem', marginBottom: '6px' }}>📦</div>
          <div style={{ fontSize: '1.6rem', fontWeight: '800', color: NAVY }}>
            {stats.totalOrders}
          </div>
          <div style={kpiLabelStyle}>Pedidos Analizados</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '1.3rem', marginBottom: '6px' }}>🏷️</div>
          <div style={{ fontSize: '1.6rem', fontWeight: '800', color: NAVY }}>
            {formatMoney(stats.avgTicket)}
          </div>
          <div style={kpiLabelStyle}>Ticket Promedio</div>
        </div>

        <div style={kpiCardStyle}>
          <div style={{ fontSize: '1.3rem', marginBottom: '6px' }}>📊</div>
          <div style={{ fontSize: '1.6rem', fontWeight: '800', color: NAVY }}>
            {stats.totalUnitsSold}
          </div>
          <div style={kpiLabelStyle}>Unidades Vendidas</div>
        </div>
      </div>

      {/* ── Gráficos e Información Principal ──────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 1fr', gap: '24px', marginBottom: '24px' }}>
        
        {/* Gráfico de ingresos mensuales */}
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2 style={cardTitleStyle}>Ingresos Mensuales</h2>
            <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Últimos 6 meses</span>
          </div>

          <div style={{ height: '220px', display: 'flex', alignItems: 'flex-end', gap: '16px', padding: '16px 0 8px' }}>
            {monthlyChartData.map((col) => (
              <div 
                key={col.month} 
                style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', height: '100%', justifyContent: 'flex-end' }} 
                title={`${col.month}: ${formatMoney(col.realValue)}`}
              >
                <div style={{ fontSize: '0.72rem', fontWeight: '700', color: col.realValue > 0 ? NAVY : '#cbd5e1' }}>
                  {col.realValue > 0 ? formatMoney(col.realValue) : '$0'}
                </div>
                <div style={{ 
                  width: '100%', 
                  height: `${Math.max(col.percentage, 4)}%`, 
                  background: col.realValue > 0 ? `linear-gradient(to top, ${NAVY}, #0056d6)` : '#f1f5f9', 
                  borderRadius: '6px 6px 0 0',
                  transition: 'height 0.4s ease',
                }} />
                <span style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: '600' }}>{col.month}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Distribución de estados */}
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <h2 style={cardTitleStyle}>Distribución por Estado</h2>
            <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Total pedidos: {orders.length}</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {statusDistribution.map(item => (
              <div key={item.status}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82rem', marginBottom: '4px' }}>
                  <span style={{ color: '#475569', fontWeight: '600' }}>{item.label}</span>
                  <span style={{ fontWeight: '700', color: item.color }}>
                    {item.count} ({item.percentage}%)
                  </span>
                </div>
                <div style={{ background: '#f1f5f9', borderRadius: '6px', height: '7px', overflow: 'hidden' }}>
                  <div style={{ width: `${item.percentage}%`, background: item.color, height: '100%', borderRadius: '6px' }} />
                </div>
              </div>
            ))}
          </div>
        </div>

      </div>

      {/* ── Ranking de Productos Más Vendidos ──────────────────────────── */}
      <div style={cardStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <h2 style={cardTitleStyle}>Productos Más Vendidos</h2>
          <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Basado en pedidos registrados</span>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.88rem' }}>
          <thead>
            <tr style={{ borderBottom: '2px solid #f1f5f9' }}>
              <th style={{ textAlign: 'left', padding: '10px 12px', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Posición</th>
              <th style={{ textAlign: 'left', padding: '10px 12px', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Producto</th>
              <th style={{ textAlign: 'right', padding: '10px 12px', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Unidades Vendidas</th>
              <th style={{ textAlign: 'right', padding: '10px 12px', color: '#94a3b8', fontSize: '0.75rem', textTransform: 'uppercase' }}>Total Generado</th>
            </tr>
          </thead>
          <tbody>
            {topProducts.length === 0 ? (
              <tr>
                <td colSpan="4" style={{ textAlign: 'center', padding: '36px', color: '#94a3b8' }}>
                  No hay ventas registradas en los pedidos filtrados. Realice compras desde el catálogo para ver datos reflejados.
                </td>
              </tr>
            ) : (
              topProducts.map((p, index) => (
                <tr key={p.id} style={{ borderBottom: index < topProducts.length - 1 ? '1px solid #f8fafc' : 'none' }}>
                  <td style={{ padding: '14px 12px', fontWeight: '800', color: index === 0 ? ORANGE : NAVY }}>
                    #{index + 1}
                  </td>
                  <td style={{ padding: '14px 12px', color: NAVY, fontWeight: '600' }}>
                    {p.name}
                  </td>
                  <td style={{ padding: '14px 12px', textAlign: 'right', fontWeight: '600', color: '#475569' }}>
                    {p.units} unidades
                  </td>
                  <td style={{ padding: '14px 12px', textAlign: 'right', color: ORANGE, fontWeight: '700' }}>
                    {formatMoney(p.revenue)}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

    </div>
  );
}

const cardStyle = {
  background: '#fff',
  borderRadius: '14px',
  padding: '24px',
  boxShadow: '0 2px 10px rgba(0,0,0,0.04)',
  borderTop: `3px solid ${ORANGE}`,
};

const cardTitleStyle = {
  margin: 0,
  fontSize: '1rem',
  fontWeight: '700',
  color: NAVY,
};

const kpiCardStyle = {
  background: '#fff',
  borderRadius: '14px',
  padding: '20px 22px',
  boxShadow: '0 2px 10px rgba(0,0,0,0.04)',
  borderTop: `3px solid ${NAVY}`,
};

const kpiLabelStyle = {
  fontSize: '0.78rem',
  color: '#94a3b8',
  marginTop: '4px',
  textTransform: 'uppercase',
  letterSpacing: '0.04em',
  fontWeight: '600',
};

export default ReportsPage;
