import { useEffect, useState } from 'react';
import useUserRoles from '../hooks/useUserRoles';
import { getOrders } from '../services/ordersService';
import { getProducts } from '../services/catalogService';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

function ReportsPage() {
  const [loading, setLoading] = useState(true);
  const [revenueData, setRevenueData] = useState([]);
  const [topProducts, setTopProducts] = useState([]);

  useEffect(() => {
    Promise.all([getOrders(), getProducts()])
      .then(([ordersRes, productsRes]) => {
        const orders = ordersRes.data;
        const products = productsRes.data;

        // Filtrar pedidos que hayan completado su ciclo (ENTREGADO)
        const completedOrders = orders.filter(o => o.status === 'ENTREGADO');

        // Calcular Ingresos Mensuales
        const monthlyRevenue = Array(12).fill(0);
        completedOrders.forEach(o => {
          const m = new Date(o.createdAt).getMonth();
          monthlyRevenue[m] += o.total || 0;
        });

        // Solo mostrar los últimos 6 meses (o los primeros 6 para el ejemplo)
        const currentMonth = new Date().getMonth();
        const startMonth = Math.max(0, currentMonth - 5);
        const chartData = [];
        let maxRev = Math.max(...monthlyRevenue.slice(startMonth, currentMonth + 1));
        if (maxRev === 0) maxRev = 1; // prevent division by zero

        for (let i = startMonth; i <= currentMonth; i++) {
          chartData.push({
            m: MONTHS[i],
            v: Math.round((monthlyRevenue[i] / maxRev) * 100),
            realValue: monthlyRevenue[i]
          });
        }
        setRevenueData(chartData);

        // Calcular Productos más vendidos
        const productStats = {};
        completedOrders.forEach(o => {
          if (o.items) {
            o.items.forEach(item => {
              if (!productStats[item.productId]) {
                productStats[item.productId] = { units: 0, revenue: 0 };
              }
              productStats[item.productId].units += item.quantity;
              productStats[item.productId].revenue += item.quantity * item.unitPrice;
            });
          }
        });

        const top = Object.keys(productStats).map(pid => {
          const product = products.find(p => p.id === parseInt(pid));
          return {
            n: product ? product.name : `Producto ${pid}`,
            u: productStats[pid].units,
            v: productStats[pid].revenue
          };
        }).sort((a, b) => b.u - a.u).slice(0, 5);

        setTopProducts(top);
      })
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return <div style={{ padding: '40px', color: '#64748b' }}>Generando reportes...</div>;
  }

  return (
    <div style={{ padding: '32px 36px', height: '100%', boxSizing: 'border-box', overflowY: 'auto' }}>
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '1.7rem', fontWeight: '800', color: NAVY, margin: '0 0 4px' }}>Reportes Financieros y Operativos</h1>
        <p style={{ color: '#64748b', margin: 0, fontSize: '0.88rem' }}>Métricas exclusivas para administradores.</p>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '24px' }}>
        
        {/* Reporte de ingresos */}
        <div style={cardStyle}>
          <h2 style={cardTitleStyle}>Ingresos Mensuales</h2>
          <div style={{ height: '200px', display: 'flex', alignItems: 'flex-end', gap: '12px', marginTop: '24px' }}>
            {revenueData.length === 0 ? <p style={{color: '#94a3b8'}}>No hay datos</p> : revenueData.map((col) => (
              <div key={col.m} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }} title={`$${col.realValue.toFixed(2)}`}>
                <div style={{ 
                  width: '100%', height: `${col.v}%`, background: `linear-gradient(to top, ${NAVY}, #004ac9)`, 
                  borderRadius: '6px 6px 0 0', minHeight: col.v > 0 ? '4px' : '0'
                }}></div>
                <span style={{ fontSize: '0.75rem', color: '#64748b' }}>{col.m}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Top Productos */}
        <div style={cardStyle}>
          <h2 style={cardTitleStyle}>Productos más vendidos</h2>
          <table style={{ width: '100%', marginTop: '16px', fontSize: '0.85rem' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #e2e8f0' }}>
                <th style={{ textAlign: 'left', padding: '8px 0', color: '#94a3b8' }}>Producto</th>
                <th style={{ textAlign: 'right', padding: '8px 0', color: '#94a3b8' }}>Unidades</th>
                <th style={{ textAlign: 'right', padding: '8px 0', color: '#94a3b8' }}>Ventas</th>
              </tr>
            </thead>
            <tbody>
              {topProducts.length === 0 ? (
                <tr><td colSpan="3" style={{textAlign: 'center', padding: '20px', color: '#94a3b8'}}>No hay ventas registradas</td></tr>
              ) : topProducts.map((p, i) => (
                <tr key={p.n} style={{ borderBottom: i < topProducts.length - 1 ? '1px solid #f1f5f9' : 'none' }}>
                  <td style={{ padding: '12px 0', color: NAVY, fontWeight: '600' }}>{p.n}</td>
                  <td style={{ padding: '12px 0', textAlign: 'right' }}>{p.u}</td>
                  <td style={{ padding: '12px 0', textAlign: 'right', color: ORANGE, fontWeight: '700' }}>${p.v.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

      </div>
    </div>
  );
}

const cardStyle = {
  background: '#fff', borderRadius: '14px', padding: '24px', 
  boxShadow: '0 2px 8px rgba(0,0,0,0.05)', borderTop: `3px solid ${ORANGE}`
};

const cardTitleStyle = {
  margin: 0, fontSize: '1rem', fontWeight: '700', color: NAVY
};

export default ReportsPage;
