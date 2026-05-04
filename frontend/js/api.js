// ══════════════════════════════════════════════════════════════
//  api.js — Wrapper para llamadas al backend REST
//
//  Agrega automáticamente el token JWT de Firebase en cada llamada.
//  Lanza Error con el mensaje del backend si el status no es 2xx.
// ══════════════════════════════════════════════════════════════

const API_BASE = 'http://localhost:8080';

async function apiCall(method, path, body = null) {
  const user = firebase.auth().currentUser;
  if (!user) throw new Error('No autenticado. Inicia sesión primero.');

  // getIdToken() refresca el token automáticamente si está por expirar
  const token = await user.getIdToken();

  const options = {
    method,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    }
  };

  if (body !== null) {
    options.body = JSON.stringify(body);
  }

  const response = await fetch(`${API_BASE}${path}`, options);
  const data = await response.json();

  if (!response.ok) {
    const msg = data.error || data.detalle || `Error ${response.status}`;
    throw new Error(msg);
  }

  return data;
}

const api = {
  get:   (path)         => apiCall('GET',   path),
  post:  (path, body)   => apiCall('POST',  path, body),
  patch: (path, body={})=> apiCall('PATCH', path, body),
};
