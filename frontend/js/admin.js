// ══════════════════════════════════════════════════════════════
//  admin.js — Dashboard del rol ADMINISTRADOR
// ══════════════════════════════════════════════════════════════

protegerPagina('ADMINISTRADOR', async (user, perfil) => {
  document.getElementById('user-nombre').textContent = perfil.nombre;
  await cargarUsuarios();
});

// ─── Tabs ─────────────────────────────────────────────────────

function mostrarTab(tab) {
  document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));

  document.getElementById(`tab-${tab}`).classList.add('active');
  const idx = { usuarios:0, pedidos:1, disputas:2 }[tab];
  document.querySelectorAll('.tab-btn')[idx].classList.add('active');

  if (tab === 'usuarios') cargarUsuarios();
  if (tab === 'pedidos')  cargarPedidos();
  if (tab === 'disputas') cargarDisputas();
}

// ─── Usuarios ─────────────────────────────────────────────────

async function cargarUsuarios() {
  const tbody = document.getElementById('tbody-usuarios');
  tbody.innerHTML = '<tr><td colspan="6" class="loading">Cargando...</td></tr>';
  try {
    const usuarios = await api.get('/api/users');
    if (!usuarios.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Sin usuarios.</td></tr>';
      return;
    }
    tbody.innerHTML = usuarios.map(u => `
      <tr>
        <td><strong>${escapeHtml(u.nombre)}</strong></td>
        <td>${escapeHtml(u.email)}</td>
        <td><span class="badge-rol ${u.rol.toLowerCase()}">${u.rol}</span></td>
        <td>
          <span style="color:${u.activo?'#27AE60':'#E74C3C'};font-weight:600">
            ${u.activo ? '● Activo' : '○ Inactivo'}
          </span>
        </td>
        <td style="font-size:13px;color:#7F8C8D">${formatFecha(u.creadoEn)}</td>
        <td>
          <div style="display:flex;gap:6px;flex-wrap:wrap">
            ${u.activo
              ? `<button class="btn btn-danger btn-sm" onclick="toggleActivo('${u.id}',false)">Desactivar</button>`
              : `<button class="btn btn-success btn-sm" onclick="toggleActivo('${u.id}',true)">Activar</button>`
            }
            <button class="btn btn-secondary btn-sm" onclick="abrirModalRol('${u.id}','${u.rol}')">Rol</button>
          </div>
        </td>
      </tr>`).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="msg msg-error">${e.message}</div></td></tr>`;
  }
}

async function toggleActivo(uid, activar) {
  const accion = activar ? 'activar' : 'desactivar';
  const ok = await confirmar(
    `${activar ? 'Activar' : 'Desactivar'} usuario`,
    `¿Confirmas que quieres ${activar ? 'activar' : 'desactivar'} esta cuenta?`,
    activar ? 'Sí, activar' : 'Sí, desactivar',
    !activar
  );
  if (!ok) return;
  try {
    await api.patch(`/api/users/${uid}/${accion}`);
    showSuccess('global-msg', `Usuario ${activar ? 'activado' : 'desactivado'} correctamente.`);
    await cargarUsuarios();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

function abrirModalRol(uid, rolActual) {
  document.getElementById('rol-uid').value = uid;
  document.getElementById('nuevo-rol').value = rolActual;
  document.getElementById('modal-rol').classList.add('active');
}

async function confirmarCambioRol() {
  const uid = document.getElementById('rol-uid').value;
  const rol = document.getElementById('nuevo-rol').value;
  try {
    await api.patch(`/api/users/${uid}/rol`, { rol });
    cerrarModal('modal-rol');
    showSuccess('global-msg', 'Rol actualizado correctamente.');
    await cargarUsuarios();
  } catch (err) {
    showError('global-msg', err.message);
  }
}

// ─── Pedidos ──────────────────────────────────────────────────

async function cargarPedidos() {
  const tbody = document.getElementById('tbody-pedidos');
  tbody.innerHTML = '<tr><td colspan="6" class="loading">Cargando...</td></tr>';
  try {
    const pedidos = await api.get('/api/orders');
    if (!pedidos.length) {
      tbody.innerHTML = '<tr><td colspan="6" class="empty-state">Sin pedidos.</td></tr>';
      return;
    }
    pedidos.sort((a,b) => new Date(b.creadoEn) - new Date(a.creadoEn));
    tbody.innerHTML = pedidos.map(p => `
      <tr>
        <td style="font-family:monospace;font-size:12px;color:#95A5A6">${p.id.slice(-8)}</td>
        <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${escapeHtml(p.descripcion)}</td>
        <td style="font-size:13px">
          <div>📍 ${escapeHtml(p.origen)}</div>
          <div>🏁 ${escapeHtml(p.destino)}</div>
        </td>
        <td><span class="badge badge-${p.estado}">${p.estado.replace('_',' ')}</span></td>
        <td style="font-family:monospace;font-size:12px;color:#95A5A6">${p.usuarioId ? p.usuarioId.slice(0,8)+'...' : '—'}</td>
        <td style="font-size:13px;color:#7F8C8D">${formatFecha(p.creadoEn)}</td>
      </tr>`).join('');
  } catch (e) {
    tbody.innerHTML = `<tr><td colspan="6"><div class="msg msg-error">${e.message}</div></td></tr>`;
  }
}

// ─── Disputas ─────────────────────────────────────────────────

async function cargarDisputas() {
  const contenedor = document.getElementById('lista-disputas');
  try {
    const disputas = await api.get('/api/disputes/abiertas');
    if (!disputas.length) {
      contenedor.innerHTML = `<div class="empty-state"><p>No hay disputas abiertas. ✅</p></div>`;
      return;
    }
    contenedor.innerHTML = disputas.map(d => `
      <div class="card" style="border-left-color:#E67E22">
        <div class="card-header">
          <span class="card-title">Disputa — Pedido ${d.pedidoId.slice(-8)}</span>
          <span class="badge badge-DISPUTADO">DISPUTADO</span>
        </div>
        <div class="card-meta">
          <span>Usuario: <code>${d.usuarioId ? d.usuarioId.slice(0,12)+'...' : '—'}</code></span>
          <span>Abierta: ${formatFecha(d.creadoEn)}</span>
        </div>
        <div style="margin:10px 0;background:#FFF3E0;padding:10px 14px;border-radius:8px;font-size:14px">
          <strong>Motivo:</strong> ${escapeHtml(d.motivo)}
        </div>
        <div class="card-actions">
          <button class="btn btn-success btn-sm" onclick="abrirModalResolver('${d.id}','${escapeHtml(d.motivo)}')">
            ✓ Resolver
          </button>
        </div>
      </div>`).join('');
  } catch (e) {
    contenedor.innerHTML = `<div class="msg msg-error">${e.message}</div>`;
  }
}

function abrirModalResolver(disputaId, motivo) {
  document.getElementById('resolver-id').value = disputaId;
  document.getElementById('resolver-motivo').textContent = `Motivo: ${motivo}`;
  document.getElementById('resolver-texto').value = '';
  hideMsg('resolver-msg');
  document.getElementById('modal-resolver').classList.add('active');
}

async function confirmarResolucion() {
  const btn = document.getElementById('btn-resolver');
  btn.disabled = true; btn.textContent = 'Guardando...';
  hideMsg('resolver-msg');

  const id         = document.getElementById('resolver-id').value;
  const resolucion = document.getElementById('resolver-texto').value.trim();

  if (!resolucion) {
    showError('resolver-msg', 'La resolución no puede estar vacía.');
    btn.disabled = false; btn.textContent = 'Resolver Disputa';
    return;
  }

  try {
    await api.patch(`/api/disputes/${id}/resolver`, { resolucion });
    cerrarModal('modal-resolver');
    showSuccess('global-msg', 'Disputa resuelta correctamente.');
    await cargarDisputas();
  } catch (err) {
    showError('resolver-msg', err.message);
  } finally {
    btn.disabled = false; btn.textContent = 'Resolver Disputa';
  }
}

// ─── Helpers ──────────────────────────────────────────────────

function cerrarModal(id) {
  document.getElementById(id).classList.remove('active');
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
