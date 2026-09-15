import { useEffect, useState } from 'react';
import useUserRoles from '../hooks/useUserRoles';

const NAVY = '#003087';
const ORANGE = '#FF6B00';

function ReportsPage() {
  const [loading, setLoading] = useState(true);

  // Simulamos carga de datos
  useEffect(() => {
    const t = setTimeout(() => setLoading(false), 800);
    return () => clearTimeout(t);
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
            {[
              { m: 'Ene', v: 40 }, { m: 'Feb', v: 60 }, { m: 'Mar', v: 45 },
              { m: 'Abr', v: 80 }, { m: 'May', v: 70 }, { m: 'Jun', v: 100 }
            ].map((col) => (
              <div key={col.m} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
                <div style={{ 
                  width: '100%', height: `${col.v}%`, background: `linear-gradient(to top, ${NAVY}, #004ac9)`, 
                  borderRadius: '6px 6px 0 0' 
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
              {[
                { n: 'Laptop XPS 13', u: 45, v: '$54,000' },
                { n: 'Monitor UltraWide', u: 32, v: '$14,400' },
                { n: 'Teclado Mecánico', u: 120, v: '$12,000' },
                { n: 'Mouse Inalámbrico', u: 98, v: '$4,900' },
              ].map((p, i) => (
                <tr key={p.n} style={{ borderBottom: i < 3 ? '1px solid #f1f5f9' : 'none' }}>
                  <td style={{ padding: '12px 0', color: NAVY, fontWeight: '600' }}>{p.n}</td>
                  <td style={{ padding: '12px 0', textAlign: 'right' }}>{p.u}</td>
                  <td style={{ padding: '12px 0', textAlign: 'right', color: ORANGE, fontWeight: '700' }}>{p.v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

      </div>

      {/* Tiempos de Entrega */}
      <div style={cardStyle}>
        <h2 style={cardTitleStyle}>Eficiencia Logística (Últimos 7 días)</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '16px', marginTop: '24px' }}>
          {[
            { label: 'Tiempo prom. de despacho', val: '4.2 horas', trend: '-12%' },
            { label: 'Entregas en < 24h', val: '94%', trend: '+2%' },
            { label: 'Paquetes extraviados', val: '0.01%', trend: '0%' },
            { label: 'Reclamos por daño', val: '0.5%', trend: '-0.2%' },
          ].map(s => (
            <div key={s.label} style={{ background: '#f8fafc', padding: '16px', borderRadius: '10px' }}>
              <div style={{ fontSize: '0.75rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{s.label}</div>
              <div style={{ fontSize: '1.4rem', fontWeight: '800', color: NAVY, marginTop: '8px' }}>{s.val}</div>
              <div style={{ fontSize: '0.75rem', color: '#15803d', fontWeight: '600', marginTop: '4px' }}>{s.trend} vs semana ant.</div>
            </div>
          ))}
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
