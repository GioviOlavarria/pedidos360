import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AccountInfo, AuthenticationResult, InteractionRequiredAuthError } from '@azure/msal-browser';
import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { msalInstance } from './msal.config';
import { roleGuard } from './role.guard';

const ACCOUNT = {
  homeAccountId: 'h',
  environment: 'login.microsoftonline.com',
  tenantId: 'tenant-1',
  username: 'cliente@outlook.com',
  localAccountId: 'l',
  idTokenClaims: {},
} as AccountInfo;

describe('seguridad del frontend', () => {
  let account: AccountInfo | null;

  beforeEach(() => {
    account = null;
    vi.spyOn(msalInstance, 'getActiveAccount').mockImplementation(() => account);
    vi.spyOn(msalInstance, 'getAllAccounts').mockImplementation(() => (account ? [account] : []));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
  });

  afterEach(() => vi.restoreAllMocks());

  function runGuard(...args: Parameters<typeof roleGuard>) {
    TestBed.inject(AuthService).refresh();
    return TestBed.runInInjectionContext(() => roleGuard(...args)({} as never, {} as never));
  }

  describe('roleGuard', () => {
    it('sin sesión redirige a /login', () => {
      const result = runGuard(['Customer']);
      expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/login');
    });

    it('un cliente no entra a reportes (Admin)', () => {
      account = ACCOUNT;
      const result = runGuard(['Admin']);
      expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/acceso-denegado');
    });

    it('usa la ruta alternativa si se indica', () => {
      account = ACCOUNT;
      const result = runGuard(['Admin', 'Operator'], '/catalog');
      expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/catalog');
    });

    it('permite la ruta con el rol correcto', () => {
      account = ACCOUNT;
      expect(runGuard(['Customer'])).toBe(true);
    });
  });

  describe('authInterceptor', () => {
    it('sin sesión envía la petición sin token', () => {
      TestBed.inject(HttpClient).get(`${environment.bffUrl}/api/catalog`).subscribe();
      const req = TestBed.inject(HttpTestingController).expectOne(`${environment.bffUrl}/api/catalog`);
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush([]);
    });

    it('con sesión adjunta el JWT pedido al mismo tenant de la cuenta', async () => {
      account = ACCOUNT;
      const silent = vi
        .spyOn(msalInstance, 'acquireTokenSilent')
        .mockResolvedValue({ accessToken: 'jwt-de-prueba' } as AuthenticationResult);

      TestBed.inject(HttpClient).get(`${environment.bffUrl}/api/orders`).subscribe();
      await vi.waitFor(() => expect(silent).toHaveBeenCalled());

      const req = TestBed.inject(HttpTestingController).expectOne(`${environment.bffUrl}/api/orders`);
      expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');
      expect(silent.mock.calls[0][0].authority).toBe('https://login.microsoftonline.com/tenant-1/');
      req.flush([]);
    });

    it('si el token requiere interacción redirige al login de Microsoft', async () => {
      account = ACCOUNT;
      vi.spyOn(msalInstance, 'acquireTokenSilent').mockRejectedValue(new InteractionRequiredAuthError('login_required', 'test-correlation'));
      const redirect = vi.spyOn(msalInstance, 'acquireTokenRedirect').mockResolvedValue();

      TestBed.inject(HttpClient).get(`${environment.bffUrl}/api/orders`).subscribe();
      await vi.waitFor(() => expect(redirect).toHaveBeenCalled());
      TestBed.inject(HttpTestingController).expectNone(`${environment.bffUrl}/api/orders`);
    });

    it('no envía el token a otros dominios', () => {
      account = ACCOUNT;
      TestBed.inject(HttpClient).get('https://otro-dominio.cl/datos').subscribe();
      const req = TestBed.inject(HttpTestingController).expectOne('https://otro-dominio.cl/datos');
      expect(req.request.headers.has('Authorization')).toBe(false);
      req.flush({});
    });
  });
});
