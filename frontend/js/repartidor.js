// ══════════════════════════════════════════════════════════════
//  repartidor.js — Dashboard del rol REPARTIDOR
// ══════════════════════════════════════════════════════════════

let _uid = null;

protegerPagina('REPARTIDOR', async (user, perfil) => {
  _uid = user.uid;
  document.getElementById('user-nombre').textContent = perfil.nombre;
  await cargarTodo();
});

async function cargarTodo() {
  hideMsg('global-msg');
  await Promise.all([cargarDisponibles(), cargarMios()]);
}

// ─── Pedidos disponibles (PENDIENTE) ──────────────────────────

async function cargarDisponibles() {
  const contenedor = document.getElementById('lista-disponibles');
  try {
    const pedidos = await api.get('/api/orders?estado=PENDIENTE');
    if (!pedidos.length) {
      contenedor.innerHTML = `<div class="empty-state"><p>No hay pedidos disponibles ahora. ¡Revisa más tarde!</p></div>`;
      return;
    }
    contenedor.innerHTML = pedidos.map(renderDisponible).join('');
  } catch (e) {
    contenedor.innerHTML = `<div class="msg msg-error">${e.message}</div>`;
  }
}

function renderDisponible(p) {
  return `
    <div class="card" style="border-left-color:#F39C12">
      <div class="card-header">
        <span class="card-title">${escapeHtml(p.descripcion)}</span>
        <span class="badge badge-PENDIENTE">PENDIENTE</span>
      </div>
      <div class="card-meta">
        <span>📍 ${escapeHtml(p.origen)}</span>
        <span>🏁 ${escapeHtml(p.destino)}</span>
        <span>📅 ${formatFecha(p.creadoEn)}</span>
      </div>
      <div class="card-actions">
        <button class="btn btn-success btn-sm" onclick="aceptarPedido('${p.id}')">
          ✓ Aceptar pedido
        </button>
      </div>
    </div>`;
}

async function aceptarPedido(pedidoId) {
  try {
    await api.patch(`/api/orders/${pedidoId}/aceptar`, { repartidorId: _uid });
    showSuccess('global-msg', 'Pedido aceptado. Ahora aparece en "Mis Pedidos".');
    await cargarTodo();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

// ─── Mis pedidos (asignados a este repartidor) ────────────────

async function cargarMios() {
  const contenedor = document.getElementById('lista-mios');
  try {
    const pedidos = await api.get(`/api/orders?repartidorId=${_uid}`);
    const activos = pedidos.filter(p => !['CANCELADO','RESUELTO'].includes(p.estado));
    activos.sort((a, b) => new Date(b.actualizadoEn) - new Date(a.actualizadoEn));

    if (!activos.length) {
      contenedor.innerHTML = `<div class="empty-state"><p>No tienes pedidos activos.</p></div>`;
      return;
    }
    contenedor.innerHTML = activos.map(renderMio).join('');
  } catch (e) {
    contenedor.innerHTML = `<div class="msg msg-error">${e.message}</div>`;
  }
}

function renderMio(p) {
  const colores = { ASIGNADO:'#3498DB', EN_CAMINO:'#9B59B6', ENTREGADO:'#27AE60', DISPUTADO:'#E67E22' };
  const borde = colores[p.estado] || '#E0E0E0';

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
        <span>📅 Actualizado: ${formatFecha(p.actualizadoEn)}</span>
      </div>
      ${historialHTML}
      <div class="card-actions">
        ${p.estado === 'ASIGNADO'   ? `<button class="btn btn-info btn-sm"    onclick="iniciarEntrega('${p.id}')">🚀 Iniciar entrega</button>` : ''}
        ${p.estado === 'EN_CAMINO' ? `<button class="btn btn-success btn-sm" onclick="confirmarEntrega('${p.id}')">✓ Confirmar entrega</button>` : ''}
        ${['ASIGNADO','EN_CAMINO','ENTREGADO','DISPUTADO'].includes(p.estado)
          ? `<button class="btn btn-secondary btn-sm" onclick="irChat('${p.id}')">💬 Chat</button>` : ''}
      </div>
    </div>`;
}

async function iniciarEntrega(pedidoId) {
  try {
    await api.patch(`/api/orders/${pedidoId}/iniciar`, { repartidorId: _uid });
    showSuccess('global-msg', '¡En camino! El usuario fue notificado.');
    await cargarMios();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

async function confirmarEntrega(pedidoId) {
  const ok = await confirmar(
    'Confirmar entrega',
    '¿El pedido fue entregado correctamente al usuario?',
    'Sí, confirmar',
    false
  );
  if (!ok) return;
  try {
    await api.patch(`/api/orders/${pedidoId}/entregar`, { repartidorId: _uid });
    showSuccess('global-msg', '¡Entrega confirmada! Buen trabajo.');
    await cargarMios();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

// ─── Chat ─────────────────────────────────────────────────────

function irChat(pedidoId) {
  window.location.href = `chat.html?pedidoId=${pedidoId}`;
}

// ─── Helper ───────────────────────────────────────────────────

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
