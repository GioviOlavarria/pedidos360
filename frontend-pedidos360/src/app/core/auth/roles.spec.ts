import { AccountInfo } from '@azure/msal-browser';
import { resolveRoles } from './roles';

function account(username: string, claims: Record<string, unknown> = {}): AccountInfo {
  return {
    homeAccountId: 'h',
    environment: 'login.microsoftonline.com',
    tenantId: 't',
    username,
    localAccountId: 'l',
    idTokenClaims: claims,
  } as AccountInfo;
}

describe('resolveRoles', () => {
  const admins = 'admin@duocuc.cl, otro@empresa.cl';

  it('sin sesión no tiene roles', () => {
    expect(resolveRoles(null, admins)).toEqual([]);
  });

  it('cualquier cuenta autenticada es Customer', () => {
    expect(resolveRoles(account('cliente@outlook.com'), admins)).toEqual(['Customer']);
  });

  it('los correos configurados son Admin (sin distinguir mayúsculas)', () => {
    expect(resolveRoles(account('Admin@DuocUC.cl'), admins)).toEqual(['Admin', 'Operator', 'Customer']);
    expect(resolveRoles(account('x', { preferred_username: 'otro@empresa.cl' }), admins)).toContain('Admin');
  });

  it('respeta los App Roles de Entra ID', () => {
    expect(resolveRoles(account('a@b.cl', { roles: ['Admin'] }), admins)).toContain('Admin');
    expect(resolveRoles(account('a@b.cl', { roles: 'Operator' }), admins)).toEqual(['Operator', 'Customer']);
  });
});
