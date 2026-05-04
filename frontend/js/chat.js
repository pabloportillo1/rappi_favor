// ══════════════════════════════════════════════════════════════
//  chat.js — Sala de chat en tiempo real con Firebase Realtime DB
//
//  Flujo:
//  1. Leer pedidoId de la URL (?pedidoId=xxx)
//  2. Llamar al backend para validar acceso y obtener el chatPath
//  3. Usar el chatPath para conectar directo a Firebase RTDB
//  4. Escuchar mensajes en tiempo real con .on('value')
//  5. Enviar mensajes con .push()
// ══════════════════════════════════════════════════════════════

let _uid       = null;
let _chatRef   = null;
let _primeraCarga = true;

protegerPagina(null, async (user, perfil) => {
  _uid = user.uid;

  const params   = new URLSearchParams(window.location.search);
  const pedidoId = params.get('pedidoId');

  if (!pedidoId) {
    document.getElementById('chat-messages').innerHTML =
      '<div class="msg msg-error" style="margin:20px">No se especificó pedidoId en la URL.</div>';
    return;
  }

  document.getElementById('chat-sub').textContent = `Pedido ${pedidoId.slice(-8)}`;

  try {
    // 1. Backend valida acceso y crea/obtiene la sala en RTDB
    const sala = await api.get(`/api/chats/${pedidoId}`);

    // 2. Conectar a Firebase RTDB con el chatPath devuelto por el backend
    const db = firebase.database();
    _chatRef = db.ref(sala.chatPath + '/mensajes');

    // 3. Escuchar mensajes en tiempo real
    _chatRef.on('value', (snapshot) => {
      const data = snapshot.val();
      renderMensajes(data);
    });

    document.getElementById('chat-sub').textContent =
      `Pedido ${pedidoId.slice(-8)} · ${perfil.nombre}`;

    document.getElementById('msg-input').focus();

  } catch (err) {
    document.getElementById('chat-messages').innerHTML =
      `<div class="msg msg-error" style="margin:20px">${err.message}</div>`;
    document.getElementById('chat-sub').textContent = 'Error al conectar';
  }
});

// ─── Renderizar mensajes ───────────────────────────────────────

function renderMensajes(data) {
  const contenedor = document.getElementById('chat-messages');

  if (!data) {
    contenedor.innerHTML = `
      <div class="empty-state" style="margin-top:40px">
        <p>Aún no hay mensajes. ¡Sé el primero en escribir!</p>
      </div>`;
    return;
  }

  const mensajes = Object.values(data).sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));

  contenedor.innerHTML = mensajes.map(m => {
    const esMio = m.autorId === _uid;
    return `
      <div class="message ${esMio ? 'mio' : 'otro'}">
        <span>${escapeHtml(m.texto)}</span>
        <span class="msg-time">${formatHora(m.timestamp)}</span>
      </div>`;
  }).join('');

  // Auto-scroll al último mensaje
  if (_primeraCarga || estaAlFinal(contenedor)) {
    contenedor.scrollTop = contenedor.scrollHeight;
    _primeraCarga = false;
  }
}

function estaAlFinal(el) {
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80;
}

// ─── Enviar mensaje ────────────────────────────────────────────

async function enviarMensaje() {
  if (!_chatRef) return;
  const input = document.getElementById('msg-input');
  const texto = input.value.trim();
  if (!texto) return;

  input.value = '';
  input.style.height = 'auto';

  try {
    await _chatRef.push({
      texto,
      autorId:   _uid,
      timestamp: Date.now()
    });
  } catch (err) {
    alert('Error al enviar el mensaje: ' + err.message);
    input.value = texto; // restaurar si falló
  }
}

// Enter envía, Shift+Enter hace salto de línea
function onKeyDown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    enviarMensaje();
  }
}

// ─── Helper ───────────────────────────────────────────────────

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
