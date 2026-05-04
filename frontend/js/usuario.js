// ══════════════════════════════════════════════════════════════
//  usuario.js — Dashboard del rol USUARIO
// ══════════════════════════════════════════════════════════════

let _uid = null;

protegerPagina('USUARIO', async (user, perfil) => {
  _uid = user.uid;
  document.getElementById('user-nombre').textContent = perfil.nombre;
  await Promise.all([cargarPedidos(), cargarDisputas()]);
});

// ─── Pedidos ──────────────────────────────────────────────────

async function cargarPedidos() {
  const contenedor = document.getElementById('lista-pedidos');
  try {
    const pedidos = await api.get(`/api/orders?usuarioId=${_uid}`);
    if (!pedidos.length) {
      contenedor.innerHTML = `<div class="empty-state"><p>Aún no tienes pedidos. ¡Crea uno!</p></div>`;
      return;
    }
    // Más recientes primero
    pedidos.sort((a, b) => new Date(b.creadoEn) - new Date(a.creadoEn));
    contenedor.innerHTML = pedidos.map(renderPedido).join('');
  } catch (e) {
    contenedor.innerHTML = `<div class="msg msg-error">${e.message}</div>`;
  }
}

function renderPedido(p) {
  const estadoColor = {
    PENDIENTE: '#F39C12', ASIGNADO: '#3498DB', EN_CAMINO: '#9B59B6',
    ENTREGADO: '#27AE60', CANCELADO: '#E74C3C', DISPUTADO: '#E67E22', RESUELTO: '#1ABC9C'
  };
  const borde = estadoColor[p.estado] || '#E0E0E0';

  const puedeChat    = ['ASIGNADO','EN_CAMINO','ENTREGADO','DISPUTADO'].includes(p.estado);
  const puedeCancelar = ['PENDIENTE','ASIGNADO'].includes(p.estado);
  const puedeDisputar = p.estado === 'ENTREGADO';

  const historialHTML = p.historial && p.historial.length
    ? `<div class="historial">
        <div class="historial-title">Historial</div>
        ${p.historial.map(h => `
          <div class="historial-item">
            <span class="badge badge-${h.estado}">${h.estado}</span>
            <span>${formatFecha(h.timestamp)}</span>
          </div>`).join('')}
       </div>`
    : '';

  return `
    <div class="card" style="border-left-color:${borde}">
      <div class="card-header">
        <span class="card-title">${escapeHtml(p.descripcion)}</span>
        <span class="badge badge-${p.estado}">${p.estado.replace('_',' ')}</span>
      </div>
      <div class="card-meta">
        <span>📍 ${escapeHtml(p.origen)}</span>
        <span>🏁 ${escapeHtml(p.destino)}</span>
        <span>📅 ${formatFecha(p.creadoEn)}</span>
      </div>
      ${historialHTML}
      <div class="card-actions">
        ${puedeChat    ? `<button class="btn btn-info btn-sm"    onclick="irChat('${p.id}')">💬 Chat</button>` : ''}
        ${puedeDisputar? `<button class="btn btn-warning btn-sm" onclick="abrirModalDisputa('${p.id}')">⚠️ Disputar</button>` : ''}
        ${puedeCancelar? `<button class="btn btn-danger btn-sm"  onclick="cancelarPedido('${p.id}')">✕ Cancelar</button>` : ''}
      </div>
    </div>`;
}

async function onCrearPedido(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-crear-pedido');
  btn.disabled = true; btn.textContent = 'Publicando...';
  hideMsg('pedido-msg');

  const descripcion = document.getElementById('p-descripcion').value.trim();
  const origen      = document.getElementById('p-origen').value.trim();
  const destino     = document.getElementById('p-destino').value.trim();

  try {
    await api.post('/api/orders', { descripcion, origen, destino, usuarioId: _uid });
    cerrarModal('modal-pedido');
    showSuccess('global-msg', 'Pedido publicado correctamente.');
    e.target.reset();
    await cargarPedidos();
  } catch (err) {
    showError('pedido-msg', err.message);
  } finally {
    btn.disabled = false; btn.textContent = 'Publicar Pedido';
  }
}

async function cancelarPedido(pedidoId) {
  if (!confirm('¿Seguro que quieres cancelar este pedido?')) return;
  try {
    await api.patch(`/api/orders/${pedidoId}/cancelar`, { usuarioId: _uid });
    showSuccess('global-msg', 'Pedido cancelado.');
    await cargarPedidos();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

// ─── Disputas ─────────────────────────────────────────────────

async function cargarDisputas() {
  const contenedor = document.getElementById('lista-disputas');
  try {
    const disputas = await api.get(`/api/disputes/usuario/${_uid}`);
    if (!disputas.length) {
      contenedor.innerHTML = `<div class="empty-state"><p>No tienes disputas abiertas.</p></div>`;
      return;
    }
    contenedor.innerHTML = disputas.map(renderDisputa).join('');
  } catch (e) {
    contenedor.innerHTML = `<div class="msg msg-error">${e.message}</div>`;
  }
}

function renderDisputa(d) {
  return `
    <div class="card" style="border-left-color:${d.estado==='RESUELTO'?'#27AE60':'#E67E22'}">
      <div class="card-header">
        <span class="card-title">Disputa — Pedido ${d.pedidoId.slice(-6)}</span>
        <span class="badge badge-${d.estado}">${d.estado}</span>
      </div>
      <div class="card-meta">
        <span>Motivo: ${escapeHtml(d.motivo)}</span>
      </div>
      ${d.resolucion ? `<div style="margin-top:8px;font-size:13px;color:#155724;background:#D4EDDA;padding:8px 12px;border-radius:8px;">
        ✅ Resolución: ${escapeHtml(d.resolucion)}
      </div>` : ''}
      <div class="card-meta" style="margin-top:8px;">
        <span>Abierta: ${formatFecha(d.creadoEn)}</span>
        ${d.resoltoEn ? `<span>Resuelta: ${formatFecha(d.resoltoEn)}</span>` : ''}
      </div>
    </div>`;
}

async function onAbrirDisputa(e) {
  e.preventDefault();
  const btn = document.getElementById('btn-disputa');
  btn.disabled = true; btn.textContent = 'Abriendo...';
  hideMsg('disputa-msg');

  const pedidoId = document.getElementById('d-pedidoId').value;
  const motivo   = document.getElementById('d-motivo').value.trim();

  try {
    await api.post('/api/disputes', { pedidoId, usuarioId: _uid, motivo });
    cerrarModal('modal-disputa');
    showSuccess('global-msg', 'Disputa abierta. Un administrador la revisará pronto.');
    e.target.reset();
    await Promise.all([cargarPedidos(), cargarDisputas()]);
  } catch (err) {
    showError('disputa-msg', err.message);
  } finally {
    btn.disabled = false; btn.textContent = 'Abrir Disputa';
  }
}

// ─── Chat ─────────────────────────────────────────────────────

function irChat(pedidoId) {
  window.location.href = `chat.html?pedidoId=${pedidoId}`;
}

// ─── Modales ──────────────────────────────────────────────────

function abrirModalPedido() {
  hideMsg('global-msg');
  hideMsg('pedido-msg');
  document.getElementById('modal-pedido').classList.add('active');
}

function abrirModalDisputa(pedidoId) {
  document.getElementById('d-pedidoId').value = pedidoId;
  hideMsg('disputa-msg');
  document.getElementById('modal-disputa').classList.add('active');
}

function cerrarModal(id) {
  document.getElementById(id).classList.remove('active');
}

// ─── Helpers ──────────────────────────────────────────────────

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
