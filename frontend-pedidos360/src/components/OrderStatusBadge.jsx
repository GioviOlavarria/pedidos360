/**
 * OrderStatusBadge
 * Pinta una etiqueta de color según el estado del pedido.
 * Estados del contrato: CREADO, ACEPTADO, EN_PREPARACION, DESPACHADO, ENTREGADO, CANCELADO
 */

const STATUS_STYLES = {
  CREADO:        { background: '#dbeafe', color: '#1d4ed8', label: 'Creado' },
  ACEPTADO:      { background: '#ede9fe', color: '#6d28d9', label: 'Aceptado' },
  EN_PREPARACION:{ background: '#fef9c3', color: '#854d0e', label: 'En preparación' },
  DESPACHADO:    { background: '#ffedd5', color: '#c2410c', label: 'Despachado' },
  ENTREGADO:     { background: '#dcfce7', color: '#15803d', label: 'Entregado' },
  CANCELADO:     { background: '#fee2e2', color: '#b91c1c', label: 'Cancelado' },
};

const BASE_STYLE = {
  display: 'inline-block',
  padding: '2px 10px',
  borderRadius: '12px',
  fontSize: '0.78rem',
  fontWeight: '600',
  letterSpacing: '0.02em',
};

function OrderStatusBadge({ status }) {
  const style = STATUS_STYLES[status] ?? { background: '#f3f4f6', color: '#374151', label: status };
  return (
    <span style={{ ...BASE_STYLE, background: style.background, color: style.color }}>
      {style.label}
    </span>
  );
}

export default OrderStatusBadge;
