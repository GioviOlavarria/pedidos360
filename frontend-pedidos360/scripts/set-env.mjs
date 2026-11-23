/**
 * Genera src/environments/environment.ts antes de compilar (npm start / npm run build).
 *
 * Los valores salen de variables de entorno (en Docker llegan como build args) o, en desarrollo,
 * de un archivo .env.local en la raíz del frontend (ver .env.local.example). Igual que antes con
 * Vite, quedan incrustados en el bundle: solo deben ser identificadores públicos, nunca secretos.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

function readDotEnv(file) {
  if (!existsSync(file)) return {};
  return Object.fromEntries(
    readFileSync(file, 'utf8')
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#') && line.includes('='))
      .map((line) => {
        const i = line.indexOf('=');
        return [line.slice(0, i).trim(), line.slice(i + 1).trim().replace(/^["']|["']$/g, '')];
      }),
  );
}

const local = readDotEnv(join(root, '.env.local'));
const value = (name, fallback) => process.env[name] || local[name] || fallback;

const environment = {
  bffUrl: value('BFF_URL', 'http://localhost:8080'),
  azureAdClientId: value('AZURE_AD_CLIENT_ID', 'a88830c8-ace4-45af-8710-18aec6f1c060'),
  azureAdTenantId: value('AZURE_AD_TENANT_ID', '868cd46e-03d0-4527-acf4-0363548bd6c6'),
  apiClientId: value('API_CLIENT_ID', ''),
  adminEmails: value('ADMIN_EMAILS', 'gi.olavarria@duocuc.cl'),
};

const target = join(root, 'src', 'environments', 'environment.ts');
mkdirSync(dirname(target), { recursive: true });
writeFileSync(
  target,
  `// Archivo generado por scripts/set-env.mjs: no editar a mano.\n` +
    `export const environment = ${JSON.stringify(environment, null, 2)};\n`,
);
console.log(`environment.ts generado (BFF: ${environment.bffUrl})`);
