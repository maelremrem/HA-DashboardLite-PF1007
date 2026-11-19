'use strict';

const $ = (id) => document.getElementById(id);
let slots = [];
let entities = [];
let focusedSlot = -1;
let tokenConfigured = false;
const actionDomains = new Set(['light', 'switch', 'fan', 'input_boolean', 'cover', 'scene', 'script', 'button', 'input_button']);

async function request(path, options) {
  const response = await fetch(path, {
    cache: 'no-store',
    credentials: 'same-origin',
    ...options,
    headers: { ...(options && options.body ? { 'Content-Type': 'application/json' } : {}) }
  });
  let data;
  try { data = await response.json(); } catch (_) { throw new Error('Réponse invalide'); }
  if (!response.ok) throw new Error(data.error || `Erreur ${response.status}`);
  return data;
}

function showError(where, message) {
  $(where).textContent = message;
}

function updateStatus() {
  request('/api/status').then((data) => {
    const m = Math.floor(data.remaining / 60);
    const s = String(data.remaining % 60).padStart(2, '0');
    $('timer').textContent = `${m}:${s}`;
    if (data.remaining === 0) location.reload();
  }).catch(() => { $('timer').textContent = 'hors ligne'; });
}

async function boot() {
  updateStatus();
  setInterval(updateStatus, 15000);
  try {
    const status = await request('/api/status');
    if (!status.authenticated) {
      $('login').classList.remove('hidden');
      return;
    }
    $('editor').classList.remove('hidden');
    await loadConfiguration();
    await loadEntities();
  } catch (e) {
    $('login').classList.remove('hidden');
    showError('loginError', e.message);
  }
}

async function loadConfiguration() {
  const data = await request('/api/config');
  $('url').value = data.url || '';
  tokenConfigured = !!data.token_set;
  $('tokenStatus').textContent = tokenConfigured
    ? 'Token enregistré sur le cadre : il reste inchangé si ce champ est vide.'
    : 'Aucun token enregistré. Ajoute un token Home Assistant pour connecter le cadre.';
  slots = data.slots.map((s) => ({ label: s.label || '', entity_id: s.entity_id || '' }));
  renderSlots();
}

function reorderSlots(items, index, direction) {
  const start = index < 3 ? 0 : 3;
  const end = index < 3 ? 3 : 9;
  const target = index + direction;
  if (index < start || index >= end || target < start || target >= end) return false;
  [items[index], items[target]] = [items[target], items[index]];
  return true;
}

function moveSlot(index, direction) {
  if (!reorderSlots(slots, index, direction)) return;
  renderSlots();
  $('message').textContent = 'Ordre modifié — enregistre pour appliquer au cadre.';
}

function renderSlots() {
  for (const [containerId, start, end] of [['metrics', 0, 3], ['controls', 3, 9]]) {
    const container = $(containerId);
    container.replaceChildren();
    for (let i = start; i < end; i++) {
      const tile = document.createElement('article');
      tile.className = 'tile';
      const heading = document.createElement('h3');
      heading.textContent = `${i < 3 ? 'Capteur' : 'Commande'} ${i < 3 ? i + 1 : i - 2}`;
      const header = document.createElement('div');
      header.className = 'tileHeader';
      const reorder = document.createElement('div');
      reorder.className = 'reorder';
      for (const direction of [-1, 1]) {
        const move = document.createElement('button');
        move.type = 'button';
        move.textContent = direction < 0 ? '↑' : '↓';
        move.title = direction < 0 ? 'Déplacer à gauche / avant' : 'Déplacer à droite / après';
        move.setAttribute('aria-label', move.title);
        move.disabled = direction < 0 ? i === start : i === end - 1;
        move.addEventListener('click', () => moveSlot(i, direction));
        reorder.append(move);
      }
      header.append(heading, reorder);
      const nameLabel = document.createElement('label');
      nameLabel.textContent = 'Titre de la carte';
      const label = document.createElement('input');
      label.value = slots[i].label;
      label.maxLength = 48;
      label.placeholder = 'Nom affiché';
      label.addEventListener('input', () => { slots[i].label = label.value; });
      const entityLabel = document.createElement('label');
      entityLabel.textContent = 'Entité Home Assistant';
      const pick = document.createElement('div');
      pick.className = 'pick';
      const entity = document.createElement('input');
      entity.value = slots[i].entity_id;
      entity.placeholder = 'Recherche ou entity_id';
      entity.spellcheck = false;
      entity.addEventListener('input', () => { slots[i].entity_id = entity.value.trim(); });
      const button = document.createElement('button');
      button.textContent = 'Chercher';
      button.type = 'button';
      button.addEventListener('click', () => openPicker(i));
      pick.append(entity, button);
      const info = document.createElement('div');
      info.className = 'hint';
      info.textContent = i < 3 ? 'Valeur et unité actualisées en direct' : 'Action au toucher, selon le type d’entité';
      tile.append(header, nameLabel, label, entityLabel, pick, info);
      container.append(tile);
    }
  }
}

async function loadEntities() {
  $('entityCount').textContent = 'Chargement des entités…';
  try {
    const data = await request('/api/entities');
    entities = data.items || [];
    entities.sort((a, b) => (a.name || a.entity_id).localeCompare(b.name || b.entity_id, 'fr'));
    $('entityCount').textContent = `${entities.length} entités disponibles`;
    if ($('picker').open) renderResults();
  } catch (e) {
    $('entityCount').textContent = 'Recherche indisponible';
    $('message').textContent = `Entités : ${e.message}. Vérifier URL et token Home Assistant.`;
  }
}

function normalize(s) {
  return (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function openPicker(index) {
  focusedSlot = index;
  $('search').value = '';
  $('picker').showModal();
  renderResults();
  $('search').focus();
}

function renderResults() {
  const term = normalize($('search').value);
  const actionsOnly = focusedSlot >= 3;
  let matches = entities.filter((e) => {
    if (actionsOnly && !actionDomains.has(e.entity_id.split('.')[0])) return false;
    return normalize(e.entity_id).includes(term) || normalize(e.name).includes(term);
  });
  const count = matches.length;
  matches = matches.slice(0, 75);
  $('pickerInfo').textContent = `${count} résultat(s)${count > 75 ? ' — 75 premiers affichés, affine la recherche' : ''}`;
  const results = $('results');
  results.replaceChildren();
  if (count === 0) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = 'Aucune entité trouvée. Tu peux aussi saisir un entity_id manuellement.';
    empty.style.padding = '12px';
    results.append(empty);
  }
  matches.forEach((e) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'result';
    const name = document.createElement('strong');
    name.textContent = e.name || e.entity_id;
    const id = document.createElement('small');
    id.textContent = e.entity_id;
    button.append(name, id);
    button.addEventListener('click', () => {
      slots[focusedSlot].entity_id = e.entity_id;
      if (!slots[focusedSlot].label.trim()) slots[focusedSlot].label = e.name || e.entity_id;
      $('picker').close();
      renderSlots();
    });
    results.append(button);
  });
}

async function saveConfiguration() {
  $('save').disabled = true;
  $('message').textContent = 'Enregistrement…';
  try {
    const payload = {
      url: $('url').value.trim(),
      token: $('token').value.trim(),
      slots: slots.map((s) => ({ label: s.label.trim(), entity_id: s.entity_id.trim() }))
    };
    if (!payload.url) throw new Error('Adresse Home Assistant manquante');
    if (!tokenConfigured && !payload.token) throw new Error('Token Home Assistant manquant');
    await request('/api/config', { method: 'POST', body: JSON.stringify(payload) });
    $('token').value = '';
    tokenConfigured = true;
    $('tokenStatus').textContent = 'Token conservé et non renvoyé dans le navigateur.';
    $('message').textContent = 'Configuration enregistrée et appliquée sur le cadre.';
  } catch (e) {
    $('message').textContent = `Erreur : ${e.message}`;
  } finally { $('save').disabled = false; }
}

$('loginForm').addEventListener('submit', async (event) => {
  event.preventDefault();
  showError('loginError', 'Connexion…');
  try {
    await request('/api/login', { method: 'POST', body: JSON.stringify({ pin: $('pin').value }) });
    $('pin').value = '';
    $('login').classList.add('hidden');
    $('editor').classList.remove('hidden');
    await loadConfiguration();
    await loadEntities();
  } catch (e) { showError('loginError', e.message); }
});
$('search').addEventListener('input', renderResults);
$('closePicker').addEventListener('click', () => $('picker').close());
$('clearEntity').addEventListener('click', () => {
  if (focusedSlot >= 0) slots[focusedSlot].entity_id = '';
  $('picker').close();
  renderSlots();
});
$('reloadEntities').addEventListener('click', loadEntities);
$('save').addEventListener('click', saveConfiguration);
$('end').addEventListener('click', async () => {
  try { await request('/api/logout', { method: 'POST', body: '{}' }); }
  catch (_) { /* Server may already be stopped. */ }
  location.reload();
});
boot();
