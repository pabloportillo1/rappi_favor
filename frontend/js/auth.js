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

// Modal de confirmación personalizado — reemplaza al confirm() del navegador
function confirmar(titulo, mensaje, btnTexto = 'Confirmar', peligroso = true) {
  return new Promise((resolve) => {
    const id = 'modal-confirm-' + Date.now();
    const div = document.createElement('div');
    div.className = 'modal active';
    div.id = id;
    div.innerHTML = `
      <div class="modal-content" style="max-width:400px;text-align:center;">
        <div style="font-size:44px;margin-bottom:12px;">${peligroso ? '⚠️' : '✅'}</div>
        <h3 style="margin-bottom:8px;font-size:18px;">${titulo}</h3>
        <p style="color:#7F8C8D;margin-bottom:24px;font-size:14px;line-height:1.5;">${mensaje}</p>
        <div style="display:flex;gap:12px;justify-content:center;">
          <button class="btn btn-secondary" id="${id}-no">Cancelar</button>
          <button class="btn ${peligroso ? 'btn-danger' : 'btn-success'}" id="${id}-si">${btnTexto}</button>
        </div>
      </div>`;
    document.body.appendChild(div);
    document.getElementById(`${id}-si`).onclick  = () => { div.remove(); resolve(true);  };
    document.getElementById(`${id}-no`).onclick  = () => { div.remove(); resolve(false); };
  });
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
