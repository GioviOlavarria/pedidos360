import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MsalProvider } from '@azure/msal-react';
import { msalInstance } from './authConfig';
import 'bootstrap/dist/css/bootstrap.min.css';
import './index.css';
import App from './App.jsx';

/**
 * MsalProvider debe envolver toda la app para que los hooks
 * useMsal(), useIsAuthenticated(), etc. estén disponibles en cualquier componente.
 * Se inicializa con la instancia ya construida en authConfig.js.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <MsalProvider instance={msalInstance}>
      <App />
    </MsalProvider>
  </StrictMode>,
);
