// ══════════════════════════════════════════════════════════════
//  auth.js — Funciones compartidas de autenticación
// ══════════════════════════════════════════════════════════════

// Muestra mensaje de error en un elemento del DOM
function showError(elementId, msg) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = msg;
  el.className = 'msg msg-error';
  el.classList.remove('hidden');
}

// Muestra mensaje de éxito
function showSuccess(elementId, msg) {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = msg;
  el.className = 'msg msg-success';
  el.classList.remove('hidden');
}

function hideMsg(elementId) {
  const el = document.getElementById(elementId);
  if (el) el.classList.add('hidden');
}

// Obtiene el perfil del usuario del backend y lo guarda en sessionStorage
async function cargarPerfil(uid) {
  const perfil = await api.get(`/api/users/${uid}`);
  sessionStorage.setItem('perfil', JSON.stringify(perfil));
  return perfil;
}

// Redirige al dashboard según el rol del usuario
async function redirigirPorRol() {
  const user = firebase.auth().currentUser;
  if (!user) return;

  let perfil;
  try {
    const guardado = sessionStorage.getItem('perfil');
    perfil = guardado ? JSON.parse(guardado) : await cargarPerfil(user.uid);
  } catch (e) {
    // Sin perfil en MongoDB todavía (registro en curso o backend apagado)
    // No redirigir — evita el loop infinito en index.html
    return;
  }

  if      (perfil.rol === 'USUARIO')        window.location.href = 'usuario.html';
  else if (perfil.rol === 'REPARTIDOR')     window.location.href = 'repartidor.html';
  else if (perfil.rol === 'ADMINISTRADOR')  window.location.href = 'admin.html';
}

// Protege una página: si no hay sesión redirige a login;
// si el rol no coincide redirige al dashboard correcto.
// rolRequerido: 'USUARIO' | 'REPARTIDOR' | 'ADMINISTRADOR' | null (cualquier rol)
function protegerPagina(rolRequerido, callback) {
  firebase.auth().onAuthStateChanged(async (user) => {
    if (!user) {
      window.location.href = 'index.html';
      return;
    }

    let perfil;
    try {
      const guardado = sessionStorage.getItem('perfil');
      perfil = guardado ? JSON.parse(guardado) : await cargarPerfil(user.uid);
    } catch (e) {
      window.location.href = 'index.html';
      return;
    }

    // Redirigir si el rol no coincide con esta página
    if (rolRequerido && perfil.rol !== rolRequerido) {
      await redirigirPorRol();
      return;
    }

    callback(user, perfil);
  });
}

async function logout() {
  sessionStorage.clear();
  await firebase.auth().signOut();
  window.location.href = 'index.html';
}

// Formatea timestamp ISO 8601 a fecha legible
function formatFecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-MX', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

// Convierte timestamp unix (milisegundos) a hora
function formatHora(ts) {
  if (!ts) return '';
  return new Date(ts).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });
}
