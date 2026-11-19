'use strict';

const $ = (id) => document.getElementById(id);
let pages = [];
let activePage = 0;
let entities = [];
let focusedCard = -1;
let tokenConfigured = false;
const MAX_PAGES = 4;
const MAX_CARDS = 12;
const CSS_PRESETS = {
  original: '',
  ocean: `:root { --accent: #58d5e7; }
body { background-color: #071c2b; }
.header, .clock { color: #b9f0ff; }
.metric, .control, .settings { background-color: #113249; border-radius: 23px; }
.control.on { background-color: #19575e; }
.control.pressed { background-color: #296b77; }
.accent { color: var(--accent); }`,
  amber: `:root { --accent: #ffb25c; }
body { background-color: #1a1412; }
.header, .clock { color: #ffe4b7; }
.metric, .control, .settings { background-color: #2d231d; border-radius: 14px; }
.control.on { background-color: #53391e; }
.control.pressed { background-color: #704d2b; }
.accent { color: var(--accent); }`,
  light: `:root { --accent: #167b72; }
body { background-color: #ebf2f5; }
.header, .clock, .control-label, .metric-value { color: #1c3445; }
.subtitle, .date, .section, .metric-label, .control-state, .status { color: #506677; }
.metric, .control, .settings { background-color: #ffffff; border-radius: 22px; }
.settings { color: #20394c; }
.control.on { background-color: #bcf1df; }
.control.pressed { background-color: #9fe3d3; }
.accent { color: var(--accent); }`
};
const PALETTE_THEMES = {
  midnight: {label:"Minuit", bg:"#0b1020", card:"#171f34", on:"#263956", pressed:"#344763", text:"#f4f7ff", muted:"#a8b7cf", accent:"#8aaaff", tab:"#2e3f6a", activeText:"#dce5ff", radius:22},
  forest: {label:"Forêt", bg:"#071c18", card:"#113129", on:"#1b4b3b", pressed:"#285a4a", text:"#e8fff3", muted:"#aad6c2", accent:"#7ce5ae", tab:"#234e3a", activeText:"#9dffd1", radius:24},
  lavender: {label:"Lavande", bg:"#171126", card:"#29213b", on:"#463558", pressed:"#56456b", text:"#f8edff", muted:"#cbb9dc", accent:"#d5aaff", tab:"#41325e", activeText:"#f9d8ff", radius:28},
  rose: {label:"Rose", bg:"#24131a", card:"#40212d", on:"#603044", pressed:"#764357", text:"#fff2f5", muted:"#ddb4c5", accent:"#ff9bb8", tab:"#653149", activeText:"#ffe4ed", radius:28},
  graphite: {label:"Graphite", bg:"#111315", card:"#262a2e", on:"#383e43", pressed:"#484f56", text:"#f0f1f2", muted:"#aeb8be", accent:"#81d5de", tab:"#394246", activeText:"#bbfaff", radius:8},
  solar: {label:"Solaire", bg:"#f8f1da", card:"#fff9ec", on:"#fde4b4", pressed:"#ecd29d", text:"#3c2b22", muted:"#795b47", accent:"#c96522", tab:"#f6ddb2", activeText:"#7b3c1c", radius:18},
  arctic: {label:"Arctique", bg:"#edf7fa", card:"#ffffff", on:"#d1eef7", pressed:"#b9e4ee", text:"#183246", muted:"#537588", accent:"#197ca5", tab:"#c5edf6", activeText:"#174d69", radius:22},
  retro: {label:"Rétro", bg:"#171c17", card:"#273326", on:"#4b5236", pressed:"#5a6442", text:"#f7f2cf", muted:"#c7c49a", accent:"#d5df74", tab:"#414d2e", activeText:"#eef0a9", radius:5},
  synthwave: {label:"Synthwave", bg:"#170e2f", card:"#291a4b", on:"#4e2166", pressed:"#6f2b86", text:"#fcddff", muted:"#d4b2ec", accent:"#ff70d9", tab:"#512164", activeText:"#ffe6ff", radius:20},
  sand: {label:"Sable", bg:"#25231f", card:"#3b352d", on:"#5d4b38", pressed:"#6a5741", text:"#fff1d5", muted:"#dac9a8", accent:"#ffd082", tab:"#665039", activeText:"#ffe4b2", radius:16}
};
function paletteCss(p) {
  return [
    `:root { --accent: ${p.accent}; }`,
    `body { background-color: ${p.bg}; }`,
    `.header, .clock, .metric-value, .settings { color: ${p.text}; }`,
    `.subtitle, .date, .status, .metric-label { color: ${p.muted}; }`,
    `.card, .settings { background-color: ${p.card}; border-radius: ${p.radius}px; }`,
    `.page-tab { background-color: ${p.card}; color: ${p.muted}; border-radius: 12px; }`,
    `.page-tab.active { background-color: ${p.tab}; color: ${p.activeText}; }`,
    `.control.on { background-color: ${p.on}; }`,
    `.control.pressed { background-color: ${p.pressed}; }`,
    ".accent { color: var(--accent); }"
  ].join("\n");
}
for (const [id,p] of Object.entries(PALETTE_THEMES)) CSS_PRESETS[id] = paletteCss(p);
const actionDomains = new Set(['light', 'switch', 'fan', 'input_boolean', 'cover', 'scene', 'script', 'button', 'input_button']);

const CARD_TYPES = [['metric', 'Capteur'], ['control', 'Commande'], ['gauge', 'Jauge'],
  ['thermostat', 'Thermostat'], ['light', 'Lumière / variateur'], ['cover', 'Volet / position']];
let displaySettings = {};

async function request(path, options) {
  const response = await fetch(path, {
    cache: 'no-store', credentials: 'same-origin', ...options,
    headers: { ...(options && options.body ? { 'Content-Type': 'application/json' } : {}) }
  });
  let data;
  try { data = await response.json(); } catch (_) { throw new Error('Réponse invalide'); }
  if (!response.ok) throw new Error(data.error || `Erreur ${response.status}`);
  return data;
}

function showError(where, message) { $(where).textContent = message; }

function updateStatus() {
  request('/api/status').then((data) => {
    const m = Math.floor(data.remaining / 60);
    $('timer').textContent = `${m}:${String(data.remaining % 60).padStart(2, '0')}`;
    if (data.remaining === 0) location.reload();
  }).catch(() => { $('timer').textContent = 'hors ligne'; });
}

async function boot() {
  updateStatus();
  setInterval(updateStatus, 15000);
  try {
    const status = await request('/api/status');
    if (!status.authenticated) { $('login').classList.remove('hidden'); return; }
    $('editor').classList.remove('hidden');
    await loadConfiguration();
    await loadEntities();
  } catch (e) {
    $('login').classList.remove('hidden');
    showError('loginError', e.message);
  }
}

function moveItem(items, index, direction) {
  const target = index + direction;
  if (index < 0 || index >= items.length || target < 0 || target >= items.length) return false;
  [items[index], items[target]] = [items[target], items[index]];
  return true;
}

function makePage(title) { return { title, columns: 3, cards: [] }; }
function makeCard(type) { return { type, label: '', entity_id: '', min: 0, max: 100 }; }

function validatePages(items) {
  if (!Array.isArray(items) || items.length < 1 || items.length > MAX_PAGES) throw new Error('1 à 4 pages requises');
  for (const [i, page] of items.entries()) {
    if (!page.title.trim() || page.title.trim().length > 32) throw new Error(`Titre invalide sur la page ${i + 1}`);
    if (![2, 3, 4].includes(Number(page.columns))) throw new Error('Choisir 2, 3 ou 4 colonnes');
    if (!Array.isArray(page.cards) || page.cards.length > Math.min(MAX_CARDS, Number(page.columns) * 3)) throw new Error('3 rangées maximum : augmente le nombre de colonnes ou retire des cartes');
    for (const card of page.cards) {
      if (!['metric', 'control', 'gauge', 'thermostat', 'light', 'cover'].includes(card.type)) throw new Error('Type de carte inconnu');
      if (!Number.isFinite(Number(card.min ?? 0)) || !Number.isFinite(Number(card.max ?? 100)) || Number(card.max ?? 100) <= Number(card.min ?? 0)) throw new Error('Plage de jauge invalide');
      if (card.label.length > 48) throw new Error('Titre de carte trop long');
      if (card.entity_id && !/^[a-z0-9_]+\.[a-z0-9_]+$/.test(card.entity_id)) throw new Error(`Entité invalide : ${card.entity_id}`);
    }
  }
}

async function loadConfiguration() {
  const data = await request('/api/config');
  $('url').value = data.url || '';
  $('dashboardTitle').value = data.dashboard_title || 'HA FRAME';
  $('customCss').value = data.custom_css || '';
  tokenConfigured = !!data.token_set;
  $('tokenStatus').textContent = tokenConfigured
    ? 'Token enregistré sur le cadre : il reste inchangé si ce champ est vide.'
    : 'Aucun token enregistré. Ajoute un token Home Assistant pour connecter le cadre.';
  displaySettings = data.display || {};
  for (const name of ['nightEnabled', 'autoBrightness', 'timeFallback', 'screenEnabled']) {
    const key = name.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
    $(name).checked = !!displaySettings[key];
  }
  for (const [id, key] of [['nightStart','night_start'], ['nightEnd','night_end'],
    ['nightLux','night_lux'], ['brightness','brightness'], ['autoMin','auto_min'],
    ['autoMax','auto_max'], ['luxEntityId','lux_entity_id']]) $(id).value = displaySettings[key] ?? '';
  pages = data.pages || [makePage('Maison')];
  pages.forEach(page => page.cards.forEach(card => { card.min ??= 0; card.max ??= 100; }));
  activePage = 0;
  renderPages();
}

function changed() { $('message').textContent = 'Modifications non enregistrées — clique sur Enregistrer.'; }

function renderPages() {
  activePage = Math.max(0, Math.min(activePage, pages.length - 1));
  const nav = $('pageTabs');
  nav.replaceChildren();
  pages.forEach((page, index) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = index === activePage ? 'pageButton selected' : 'pageButton';
    btn.textContent = page.title || `Page ${index + 1}`;
    btn.addEventListener('click', () => { activePage = index; renderPages(); });
    nav.append(btn);
  });
  $('addPage').disabled = pages.length >= MAX_PAGES;
  $('removePage').disabled = pages.length <= 1;
  $('prevPage').disabled = activePage === 0;
  $('nextPage').disabled = activePage === pages.length - 1;
  const page = pages[activePage];
  $('pageName').value = page.title;
  $('pageColumns').value = String(page.columns);
  const limit = Math.min(MAX_CARDS, page.columns * 3);
  $('cardCount').textContent = `${page.cards.length}/${limit} cartes`;
  for (const id of ['addMetric','addControl','addGauge','addThermostat','addLight','addCover']) $(id).disabled = page.cards.length >= limit;
  renderCards();
}

function addPage() {
  if (pages.length >= MAX_PAGES) return;
  pages.push(makePage(`Page ${pages.length + 1}`));
  activePage = pages.length - 1;
  renderPages(); changed();
}

function removePage() {
  if (pages.length <= 1) return;
  if (!confirm(`Supprimer la page « ${pages[activePage].title} » et ses cartes ?`)) return;
  pages.splice(activePage, 1);
  activePage = Math.min(activePage, pages.length - 1);
  renderPages(); changed();
}

function movePage(direction) {
  if (!moveItem(pages, activePage, direction)) return;
  activePage += direction;
  renderPages(); changed();
}

function addCard(type) {
  if (pages[activePage].cards.length >= Math.min(MAX_CARDS, pages[activePage].columns * 3)) return;
  pages[activePage].cards.push(makeCard(type));
  renderPages(); changed();
}

function editCardType(index, type) {
  const card = pages[activePage].cards[index];
  card.type = type;
  if (card.entity_id) {
    const domain = card.entity_id.split('.')[0];
    if ((type === 'control' && !actionDomains.has(domain)) ||
        (type === 'thermostat' && domain !== 'climate') ||
        (type === 'light' && domain !== 'light') || (type === 'cover' && domain !== 'cover')) {
      card.entity_id = '';
    }
  }
  renderCards(); changed();
}

function moveCard(index, direction) {
  if (!moveItem(pages[activePage].cards, index, direction)) return;
  renderCards(); changed();
}

function removeCard(index) {
  pages[activePage].cards.splice(index, 1);
  renderPages(); changed();
}

function renderCards() {
  const cards = pages[activePage].cards;
  const grid = $('cards');
  grid.replaceChildren();
  for (let i = 0; i < cards.length; i++) {
    const card = cards[i];
    const tile = document.createElement('article');
    tile.className = 'tile';
    const heading = document.createElement('h3');
    heading.textContent = `Carte ${i + 1}`;
    const header = document.createElement('div');
    header.className = 'tileHeader';
    const buttons = document.createElement('div');
    buttons.className = 'reorder';
    for (const dir of [-1, 1]) {
      const b = document.createElement('button');
      b.type = 'button';
      b.textContent = dir < 0 ? '↑' : '↓';
      b.title = dir < 0 ? 'Déplacer avant' : 'Déplacer après';
      b.disabled = dir < 0 ? i === 0 : i === cards.length - 1;
      b.addEventListener('click', () => moveCard(i, dir));
      buttons.append(b);
    }
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = '✕';
    remove.title = 'Supprimer la carte';
    remove.addEventListener('click', () => removeCard(i));
    buttons.append(remove);
    header.append(heading, buttons);
    const typeLabel = document.createElement('label');
    typeLabel.textContent = 'Type';
    const type = document.createElement('select');
    for (const [value, label] of CARD_TYPES) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      type.append(option);
    }
    type.value = card.type;
    type.addEventListener('change', () => editCardType(i, type.value));
    const titleLabel = document.createElement('label');
    titleLabel.textContent = 'Titre de la carte';
    const title = document.createElement('input');
    title.value = card.label;
    title.maxLength = 48;
    title.placeholder = 'Nom affiché';
    title.addEventListener('input', () => { card.label = title.value; changed(); });
    const entityLabel = document.createElement('label');
    entityLabel.textContent = 'Entité Home Assistant';
    const pick = document.createElement('div');
    pick.className = 'pick';
    const entity = document.createElement('input');
    entity.value = card.entity_id;
    entity.placeholder = 'Recherche ou entity_id';
    entity.spellcheck = false;
    entity.addEventListener('input', () => { card.entity_id = entity.value.trim(); changed(); });
    const picker = document.createElement('button');
    picker.textContent = 'Chercher';
    picker.type = 'button';
    picker.addEventListener('click', () => openPicker(i));
    pick.append(entity, picker);
    tile.append(header, typeLabel, type, titleLabel, title, entityLabel, pick);
    if (card.type === 'gauge') {
      const range = document.createElement('div');
      range.className = 'fields';
      for (const [field, text] of [['min','Valeur minimum'], ['max','Valeur maximum']]) {
        const wrapper = document.createElement('div');
        const label = document.createElement('label'); label.textContent = text;
        const input = document.createElement('input'); input.type = 'number'; input.value = card[field];
        input.addEventListener('input', () => { card[field] = Number(input.value); changed(); });
        wrapper.append(label, input); range.append(wrapper);
      }
      tile.append(range);
    }
    grid.append(tile);
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
    $('message').textContent = `Entités : ${e.message}. Vérifie l’URL et le token.`;
  }
}

function normalize(s) { return (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim(); }

function openPicker(index) {
  focusedCard = index;
  $('search').value = '';
  $('picker').showModal();
  renderResults();
  $('search').focus();
}

function renderResults() {
  const term = normalize($('search').value);
  const card = focusedCard === -2 ? {type: 'lux'} : pages[activePage].cards[focusedCard];
  if (!card) return;
  let matches = entities.filter((e) => {
    if (card.type === 'control' && !actionDomains.has(e.entity_id.split('.')[0])) return false;
    if (card.type === 'lux' && !e.entity_id.startsWith('sensor.')) return false;
    if (card.type === 'thermostat' && !e.entity_id.startsWith('climate.')) return false;
    if (card.type === 'light' && !e.entity_id.startsWith('light.')) return false;
    if (card.type === 'cover' && !e.entity_id.startsWith('cover.')) return false;
    return normalize(e.entity_id).includes(term) || normalize(e.name).includes(term);
  });
  const count = matches.length;
  matches = matches.slice(0, 75);
  $('pickerInfo').textContent = `${count} résultat(s)${count > 75 ? ' — affine la recherche' : ''}`;
  const results = $('results');
  results.replaceChildren();
  if (!matches.length) {
    const text = document.createElement('p');
    text.textContent = 'Aucune entité trouvée. Tu peux aussi saisir un entity_id manuellement.';
    text.className = 'muted';
    results.append(text);
  }
  matches.forEach((e) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'result';
    const name = document.createElement('strong');
    name.textContent = e.name || e.entity_id;
    const id = document.createElement('small');
    id.textContent = e.entity_id;
    btn.append(name, id);
    btn.addEventListener('click', () => {
      if (focusedCard === -2) $('luxEntityId').value = e.entity_id;
      else { card.entity_id = e.entity_id; if (!card.label.trim()) card.label = e.name || e.entity_id; }
      $('picker').close();
      renderCards(); changed();
    });
    results.append(btn);
  });
}

async function saveConfiguration() {
  $('save').disabled = true;
  $('message').textContent = 'Enregistrement…';
  try {
    displaySettings = {
      night_enabled: $('nightEnabled').checked, auto_brightness: $('autoBrightness').checked,
      time_fallback: $('timeFallback').checked, screen_enabled: $('screenEnabled').checked,
      night_start: $('nightStart').value, night_end: $('nightEnd').value,
      night_lux: Number($('nightLux').value), brightness: Number($('brightness').value),
      auto_min: Number($('autoMin').value), auto_max: Number($('autoMax').value),
      lux_entity_id: $('luxEntityId').value.trim()
    };
    const payload = {
      url: $('url').value.trim(),
      token: $('token').value.trim(),
      dashboard_title: $('dashboardTitle').value.trim() || 'HA FRAME',
      custom_css: $('customCss').value,
      display: displaySettings,
      pages: pages.map((page) => ({
        title: page.title.trim(), columns: Number(page.columns),
        cards: page.cards.map((card) => ({
          type: card.type, label: card.label.trim(), entity_id: card.entity_id.trim(), min: card.min ?? 0, max: card.max ?? 100
        }))
      }))
    };
    if (!payload.url) throw new Error('Adresse Home Assistant manquante');
    if (!tokenConfigured && !payload.token) throw new Error('Token Home Assistant manquant');
    validatePages(payload.pages);
    await request('/api/config', { method: 'POST', body: JSON.stringify(payload) });
    $('token').value = '';
    tokenConfigured = true;
    $('tokenStatus').textContent = 'Token conservé et non renvoyé dans le navigateur.';
    $('message').textContent = 'Configuration enregistrée et appliquée sur le cadre.';
    $('cssMessage').textContent = 'CSS accepté par le moteur natif.';
  } catch (e) {
    $('message').textContent = `Erreur : ${e.message}`;
    $('cssMessage').textContent = `Validation : ${e.message}`;
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
$('addPage').addEventListener('click', addPage);
$('removePage').addEventListener('click', removePage);
$('prevPage').addEventListener('click', () => movePage(-1));
$('nextPage').addEventListener('click', () => movePage(1));
$('addMetric').addEventListener('click', () => addCard('metric'));
$('addControl').addEventListener('click', () => addCard('control'));
for (const [id, type] of [['addGauge','gauge'], ['addThermostat','thermostat'],
  ['addLight','light'], ['addCover','cover']]) $(id).addEventListener('click', () => addCard(type));
$('pickLux').addEventListener('click', () => openPicker(-2));
$('pageName').addEventListener('input', () => {
  pages[activePage].title = $('pageName').value;
  const nav = $('pageTabs').children[activePage];
  if (nav) nav.textContent = $('pageName').value || `Page ${activePage + 1}`;
  changed();
});
$('pageColumns').addEventListener('change', () => {
  const page = pages[activePage];
  const columns = Number($('pageColumns').value);
  if (page.cards.length > columns * 3) {
    $('message').textContent = `Impossible : ${page.cards.length} cartes nécessitent au moins ${Math.ceil(page.cards.length / 3)} colonnes.`;
    $('pageColumns').value = String(page.columns);
    return;
  }
  page.columns = columns;
  renderPages(); changed();
});
$('search').addEventListener('input', renderResults);
$('customCss').addEventListener('input', () => {
  $('cssMessage').textContent = 'Modifications non enregistrées.';
});
$('usePreset').addEventListener('click', () => {
  const preset = CSS_PRESETS[$('themePreset').value];
  if (preset === undefined) return;
  if ($('customCss').value.trim() && !confirm('Remplacer le CSS actuel par ce thème ?')) return;
  $('customCss').value = preset;
  $('cssMessage').textContent = 'Thème chargé. Clique sur Enregistrer pour appliquer.';
});
$('exportCss').addEventListener('click', () => {
  const blob = new Blob([$('customCss').value], { type: 'text/css;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'ha-frame-theme.css';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
});
$('importCss').addEventListener('click', () => $('cssFile').click());
$('cssFile').addEventListener('change', async () => {
  const file = $('cssFile').files[0];
  if (!file) return;
  if (file.size > 12000) { $('cssMessage').textContent = 'Fichier CSS trop long (12 Ko max).'; return; }
  if ($('customCss').value.trim() && !confirm('Remplacer le CSS actuel par ce fichier ?')) return;
  try {
    $('customCss').value = await file.text();
    $('cssMessage').textContent = 'CSS importé. Enregistre pour appliquer.';
  } catch (e) { $('cssMessage').textContent = `Import impossible : ${e.message}`; }
  finally { $('cssFile').value = ''; }
});
$('closePicker').addEventListener('click', () => $('picker').close());
$('clearEntity').addEventListener('click', () => {
  const card = focusedCard === -2 ? null : pages[activePage].cards[focusedCard];
  if (focusedCard === -2) $('luxEntityId').value = '';
  if (card) { card.entity_id = ''; changed(); }
  $('picker').close();
  renderCards();
});
$('reloadEntities').addEventListener('click', loadEntities);
$('save').addEventListener('click', saveConfiguration);
$('end').addEventListener('click', async () => {
  try { await request('/api/logout', { method: 'POST', body: '{}' }); }
  catch (_) { /* Server may already be stopped. */ }
  location.reload();
});
boot();
