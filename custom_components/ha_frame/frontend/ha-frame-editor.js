/* HA Frame visual editor - bundled with the Home Assistant integration. */
const CARD_VERSION = "1.3.0";
const TYPES = [
  ["metric", "Capteur"], ["control", "Commande"], ["gauge", "Jauge"],
  ["thermostat", "Thermostat"], ["light", "Lumière"], ["cover", "Volet"],
  ["weather", "Météo"], ["graph", "Courbe"], ["camera", "Caméra (MJPEG)"]
];
const ICON_OPTIONS = [
  ["","Automatique"],["none","Aucune"],["home","Maison"],["light","Ampoule"],
  ["thermometer","Thermomètre"],["energy","Énergie"],["water","Eau"],
  ["sun","Soleil"],["cloud","Nuage"],["lock","Cadenas"],["camera","Caméra"],
  ["chart","Graphique"],["fan","Ventilation"],["battery","Batterie"],
  ["wifi","Wi-Fi"],["person","Présence"],["settings","Réglages"],
  ["bell","Cloche"],["door","Porte"],["plug","Prise"],["moon","Lune"],
  ["heart","Cœur"]
];
const ICON_PATHS = {
  home:'<path d="M3 11 12 3 21 11M5 10v11h14V10M10 21v-6h4v6"/>',
  light:'<circle cx="12" cy="9" r="6"/><path d="M9 15v4h6v-4M10 22h4"/>',
  thermometer:'<rect x="10" y="3" width="4" height="14" rx="2"/><circle cx="12" cy="18" r="4"/><path d="M12 8v11"/>',
  energy:'<path d="m13 2-8 11h7l-2 9 10-12h-7z"/>',
  water:'<path d="M12 2S5 11 5 15a7 7 0 0 0 14 0C19 11 12 2 12 2Z"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v3M12 19v3M2 12h3m14 0h3M4 4l2 2m12 12 2 2M20 4l-2 2M6 18l-2 2"/>',
  cloud:'<path d="M5 18a4 4 0 0 1 1-8 7 7 0 0 1 13 1 4 4 0 0 1-1 8H6"/>',
  lock:'<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  camera:'<rect x="2" y="6" width="20" height="14" rx="3"/><circle cx="12" cy="13" r="4"/><path d="m7 6 2-3h6l2 3"/>',
  chart:'<path d="M3 3v18h19M5 17l5-5 4 3 7-10"/>',
  fan:'<circle cx="12" cy="12" r="2"/><path d="M12 10C6 4 10 0 14 3s1 7-2 7ZM14 12c9-1 11 4 6 7s-8-1-8-5ZM10 14c-5 8-10 6-10 1s5-6 10-3Z"/>',
  battery:'<rect x="2" y="7" width="18" height="10" rx="2"/><path d="M22 10v4M6 12h9"/>',
  wifi:'<path d="M2 8a16 16 0 0 1 20 0M5 12a10 10 0 0 1 14 0m-11 4a6 6 0 0 1 8 0"/><circle cx="12" cy="20" r="1"/>',
  person:'<circle cx="12" cy="7" r="4"/><path d="M3 22a9 9 0 0 1 18 0"/>',
  settings:'<circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3"/>',
  bell:'<path d="M5 17l2-3v-4a5 5 0 0 1 10 0v4l2 3zM10 19a2 2 0 0 0 4 0"/>',
  door:'<rect x="5" y="3" width="14" height="19"/><circle cx="15" cy="13" r="1"/>',
  plug:'<path d="M8 3v7m8-7v7M5 10a7 7 0 0 0 14 0M12 17v5"/>',
  moon:'<path d="M18 3C8 2 5 14 13 18c3 2 7 0 8-2-4 8-14 8-17 0C1 8 10 0 18 3Z"/>',
  heart:'<path d="M12 21C9 18 2 13 2 8 2 1 9 2 12 6c3-4 10-5 10 2 0 5-7 11-10 13Z"/>'
};
const AUTO_ICONS = {light:"light",thermostat:"thermometer",weather:"sun",
  graph:"chart",camera:"camera",cover:"door",gauge:"chart"};
function tileIconMarkup(icon, type, color) {
  const name = icon === "none" ? "" : icon || AUTO_ICONS[type] || "";
  const paths = ICON_PATHS[name];
  if (!paths) return "";
  return "<svg class='tile-icon' viewBox='0 0 24 24' fill='none' stroke='" +
    color + "' stroke-width='2' stroke-linecap='round' stroke-linejoin='round' " +
    "aria-label='" + esc(name) + "'>" + paths + "</svg>";
}

const PRESETS = {
  original: "",
  ocean: [
    ":root { --accent: #58d5e7; }", "body { background-color: #071c2b; }",
    ".header, .clock { color: #b9f0ff; }",
    ".metric, .control, .settings { background-color: #113249; border-radius: 23px; }",
    ".control.on { background-color: #19575e; }",
    ".control.pressed { background-color: #296b77; }",
    ".accent { color: var(--accent); }"
  ].join("\n"),
  amber: [
    ":root { --accent: #ffb25c; }", "body { background-color: #1a1412; }",
    ".header, .clock { color: #ffe4b7; }",
    ".metric, .control, .settings { background-color: #2d231d; border-radius: 14px; }",
    ".control.on { background-color: #53391e; }",
    ".control.pressed { background-color: #704d2b; }",
    ".accent { color: var(--accent); }"
  ].join("\n"),
  light: [
    ":root { --accent: #167b72; }", "body { background-color: #ebf2f5; }",
    ".header, .clock, .control-label, .metric-value { color: #1c3445; }",
    ".subtitle, .date, .section, .metric-label, .control-state, .status { color: #506677; }",
    ".metric, .control, .settings { background-color: #ffffff; border-radius: 22px; }",
    ".settings { color: #20394c; }",
    ".control.on { background-color: #bcf1df; }",
    ".control.pressed { background-color: #9fe3d3; }",
    ".accent { color: var(--accent); }"
  ].join("\n")
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
for (const [id,p] of Object.entries(PALETTE_THEMES)) PRESETS[id] = paletteCss(p);
const THEME_LABELS = {
  original:"Original", ocean:"Océan", amber:"Ambre", light:"Clair",
  ...Object.fromEntries(Object.entries(PALETTE_THEMES).map(([id,p])=>[id,p.label]))
};
const THEME_SWATCHES = {
  original:{bg:"#10151e",card:"#1b2432",text:"#f5f7fb",accent:"#67dbba"},
  ocean:{bg:"#071c2b",card:"#113249",text:"#b9f0ff",accent:"#58d5e7"},
  amber:{bg:"#1a1412",card:"#2d231d",text:"#ffe4b7",accent:"#ffb25c"},
  light:{bg:"#ebf2f5",card:"#ffffff",text:"#1c3445",accent:"#167b72"}
};
const SHAPES = {
  standard: {label:"Standard", radius:20},
  square: {label:"Anguleux", radius:4},
  subtle: {label:"Discret", radius:12},
  soft: {label:"Arrondi", radius:28},
  bubble: {label:"Bulle", radius:36}
};
const TYPOGRAPHY = {
  standard: {label:"Standard"},
  compact: {label:"Compact",clock:32,heading:30,value:28},
  large: {label:"Grande lisibilité",clock:52,heading:40,value:42}
};
const DESIGN_START = "/* HA Frame Studio design */";
const DESIGN_END = "/* Fin HA Frame Studio design */";
function removeDesign(css) {
  return String(css || "").replace(/\/\* HA Frame Studio design \*\/[\s\S]*?\/\* Fin HA Frame Studio design \*\//g, "").trim();
}
function readDesignBlock(css) {
  const found = String(css || "").match(/\/\* HA Frame Studio design \*\/([\s\S]*?)\/\* Fin HA Frame Studio design \*\//);
  return found ? found[1] : "";
}
function readShape(css) {
  const m = readDesignBlock(css).match(/\.card, \.settings \{ border-radius: (\d+)px; \}/);
  return Object.keys(SHAPES).find(k=>m && SHAPES[k].radius === Number(m[1])) || "standard";
}
function readTypography(css) {
  const m = readDesignBlock(css).match(/\.clock \{ font-size: (\d+)px; \}/);
  return Object.keys(TYPOGRAPHY).find(k=>m && TYPOGRAPHY[k].clock === Number(m[1])) || "standard";
}
function withDesign(css, shape, typography) {
  if (!SHAPES[shape] || !TYPOGRAPHY[typography]) throw new Error("Design inconnu");
  const base = removeDesign(css);
  if (shape === "standard" && typography === "standard") return base;
  const changes = [];
  if (shape !== "standard") {
    const radius = SHAPES[shape].radius;
    changes.push(`.card, .settings { border-radius: ${radius}px; }`);
    changes.push(`.page-tab { border-radius: ${Math.round(radius/2)}px; }`);
  }
  if (typography !== "standard") {
    const t = TYPOGRAPHY[typography];
    changes.push(`.clock { font-size: ${t.clock}px; }`);
    changes.push(`.header { font-size: ${t.heading}px; }`);
    changes.push(`.metric-value { font-size: ${t.value}px; }`);
  }
  return [base, DESIGN_START, ...changes, DESIGN_END].filter(Boolean).join("\n");
}
function withShape(css, shape) { return withDesign(css, shape, readTypography(css)); }
function withTypography(css, typography) { return withDesign(css, readShape(css), typography); }

const TILE_STYLE_KEYS = ["background", "foreground", "accent", "radius"];
function gridPlacement(page) {
  const cols = page.columns;
  if (![2,3,4].includes(cols) || page.cards.length > cols*3) throw new Error("Grille invalide");
  const used = Array.from({length:3},()=>Array(cols).fill(false));
  const cells = [];
  let rows = 1;
  for (const tile of page.cards) {
    const w = tile.col_span == null ? 1 : tile.col_span;
    const h = tile.row_span == null ? 1 : tile.row_span;
    if (!Number.isInteger(w) || !Number.isInteger(h) || w < 1 || w > cols || h < 1 || h > 2) {
      throw new Error("Taille de tuile invalide");
    }
    let position = null;
    for (let row=0; row+h<=3 && !position; row++) {
      for (let col=0; col+w<=cols; col++) {
        let free = true;
        for (let y=row; y<row+h; y++) for (let x=col; x<col+w; x++) {
          if (used[y][x]) free=false;
        }
        if (!free) continue;
        for (let y=row; y<row+h; y++) for (let x=col; x<col+w; x++) used[y][x]=true;
        position={col,row,w,h};
        rows=Math.max(rows,row+h);
        break;
      }
    }
    if (!position) throw new Error("Pas assez de place pour ces tailles de tuiles");
    cells.push(position);
  }
  return {cells,rows,used:page.cards.reduce((n,t)=>n+(t.col_span||1)*(t.row_span||1),0)};
}
function previewCss(rules, scale, ...names) {
  const combined = {};
  for (const name of names) Object.assign(combined, rules[name] || {});
  for (const key of ["font-size","border-radius"]) {
    if (combined[key]) combined[key]=Math.max(key==="font-size"?7:0,
      Number.parseInt(combined[key],10)*scale).toFixed(1)+"px";
  }
  return attr(combined);
}
function validTileStyle(style) {
  if (!style || typeof style !== "object" || Array.isArray(style)) return false;
  if (Object.keys(style).some(key=>!TILE_STYLE_KEYS.includes(key))) return false;
  return ["background","foreground","accent"].every(key=>style[key] == null ||
    (typeof style[key] === "string" && /^#[0-9a-fA-F]{6}$/.test(style[key]))) &&
    (style.radius == null || (Number.isInteger(style.radius) && style.radius>=0 && style.radius<=72));
}
function isAdvancedPage(page) {
  return page.cards.some(tile=>tile.type==="weather" || tile.type==="graph" ||
    (tile.col_span||1)!==1 || (tile.row_span||1)!==1 ||
    (tile.style && Object.keys(tile.style).length>0));
}
function compatibleVersion(version) {
  const parts=String(version||"").match(/^(\d+)\.(\d+)\.(\d+)/);
  return Boolean(parts && Number(parts[1])>=1);
}

const LAYOUT_TEMPLATES = [
  {id:"overview", name:"Vue générale", columns:3, tiles:[
    ["metric","Température"], ["metric","Humidité"], ["metric","Puissance"],
    ["light","Salon"], ["control","Mode maison"], ["cover","Volets"]]},
  {id:"climate", name:"Climat", columns:2, tiles:[
    ["thermostat","Thermostat"], ["metric","Intérieur"],
    ["metric","Extérieur"], ["gauge","Humidité"]]},
  {id:"energy", name:"Énergie", columns:3, tiles:[
    ["metric","Puissance"], ["metric","Consommation"], ["metric","Production"],
    ["gauge","Batterie"], ["metric","Énergie du jour"], ["control","Mode éco"]]},
  {id:"lights", name:"Éclairages", columns:3, tiles:[
    ["light","Salon"], ["light","Cuisine"], ["light","Bureau"],
    ["light","Chambre"], ["light","Extérieur"], ["light","Ambiance"]]},
  {id:"security", name:"Sécurité", columns:3, tiles:[
    ["control","Alarme"], ["metric","Entrée"], ["metric","Garage"],
    ["cover","Porte garage"], ["light","Extérieur"], ["metric","Présence"]]},
  {id:"dense", name:"12 tuiles", columns:4, tiles:Array.from({length:12}, (_,i)=>["metric","Capteur "+(i+1)])},
  {id:"weather-home",name:"Météo & maison",columns:3,tiles:[
    ["weather","Météo"],["metric","Température"],["metric","Humidité"],["light","Salon"],["control","Maison"]],
    spans:[[2,1],[1,1],[1,1],[1,1],[1,1]]},
  {id:"energy-trend",name:"Énergie & tendance",columns:3,tiles:[
    ["graph","Courbe de puissance"],["metric","Puissance"],["gauge","Batterie"],["metric","Énergie totale"]],
    spans:[[2,2],[1,1],[1,1],[1,1]]}
];
function templatePage(template) {
  return {
    title: template.name, columns: template.columns,
    cards: template.tiles.map(([type,label],i)=>{
      const tile={type,label,entity_id:"",min:0,max:100};
      if (template.spans && template.spans[i]) {
        const [w,h]=template.spans[i];
        if (w>1) tile.col_span=w;
        if (h>1) tile.row_span=h;
      }
      return tile;
    })
  };
}

const STYLE = [
  ":host{display:block;color:var(--primary-text-color,#223047);font:inherit;min-width:0}",
  "ha-card{display:block;overflow:hidden;padding:0;background:var(--ha-card-background,var(--card-background-color,#fff));border-radius:18px}",
  ".top{padding:17px 20px 13px;border-bottom:1px solid var(--divider-color,#ddd)}",
  ".brand{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}",
  ".heading{font-size:18px;font-weight:700;letter-spacing:-.4px}.sub{font-size:12px;opacity:.67;margin-top:3px}",
  ".row{display:flex;gap:9px;align-items:center;flex-wrap:wrap}.spacer{flex:1}",
  "select,input,textarea,button{box-sizing:border-box;font:inherit;color:inherit}",
  "select,input,textarea{border:1px solid var(--divider-color,#cbd3dc);border-radius:9px;background:var(--card-background-color,#fff);padding:9px 10px;min-width:0}",
  "select:focus,input:focus,textarea:focus{outline:2px solid var(--primary-color,#327dc5);outline-offset:1px}",
  "button{border:1px solid var(--divider-color,#cbd3dc);background:var(--secondary-background-color,#f4f6f9);border-radius:9px;padding:8px 11px;cursor:pointer}",
  "button:hover{border-color:var(--primary-color,#327dc5)}button:disabled{opacity:.35;cursor:default}",
  ".primary{background:var(--primary-color,#327dc5);border-color:transparent;color:white;font-weight:650}",
  ".danger{color:var(--error-color,#d03b46)}.btn-small{padding:5px 8px;font-size:12px}",
  ".toolbar{margin-top:14px}.toolbar label{font-size:12px;opacity:.75}",
  ".frame-select{min-width:205px;max-width:100%}",
  ".status{font-size:12px;min-height:18px;opacity:.75}.status.error{color:var(--error-color,#d03b46);opacity:1}",
  ".content{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(260px,.75fr);gap:0;min-height:380px}",
  ".canvas-area{padding:17px;background:var(--secondary-background-color,#f5f7fa);min-width:0}",
  ".canvas-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:12px}",
  ".canvas-head strong{font-size:13px}.muted{opacity:.62;font-size:11px}",
  ".screen-wrap{max-width:980px;margin:0 auto;border-radius:16px;background:#05080c;padding:7px;box-shadow:0 8px 30px #0002}",
  ".screen{position:relative;aspect-ratio:8 / 5;overflow:hidden;border-radius:10px;color:#f5f7fb;background:#10151e;container-type:inline-size}",
  ".screen-inner{position:absolute;inset:0;overflow:hidden}",
  ".screen-header{position:absolute;top:3.5%;left:3.9%;right:3.9%;display:flex;align-items:flex-start;justify-content:space-between;gap:1cqw}",
  ".screen-title{font-size:clamp(11px,2.7cqw,36px);font-weight:700;line-height:1.1;max-width:60cqw;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
  ".screen-sub{font-size:clamp(7px,1.48cqw,19px);margin-top:.45cqw;opacity:.64}",
  ".screen-online{font-size:clamp(7px,1.25cqw,16px);opacity:.7;white-space:nowrap;max-width:22cqw;overflow:hidden}",
  ".screen-clock{position:absolute;top:17%;left:3.9%;right:3.9%;display:flex;align-items:center;gap:2cqw;font-size:clamp(13px,3.13cqw,40px);line-height:1.1;font-weight:700}",
  ".screen-date{font-weight:400;font-size:clamp(8px,1.48cqw,19px);opacity:.66}",
  ".screen-tabs{position:absolute;top:26.375%;left:3.9%;right:3.9%;height:5.75%;display:flex;gap:.85cqw;min-height:0}",
  ".screen-tab{display:flex;align-items:center;flex:1;min-width:0;border-radius:1.05cqw;padding:0 1.25cqw;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:clamp(7px,1.4cqw,18px);background:#1b2432;color:#94a3b8}",
  ".screen-tab.active{background:#213d3c;color:#67dbba;font-weight:700}",
  ".screen-grid{position:absolute;left:3.90625%;right:3.90625%;top:34.5%;height:59%;display:grid;column-gap:1.2712%;row-gap:3.178%;align-content:start;min-height:0;min-width:0}",
  ".screen-tile{box-sizing:border-box;border:1px solid transparent;min-width:0;min-height:0;border-radius:1.55cqw;padding:clamp(4px,1.25cqw,18px);background:#1b2432;display:flex;flex-direction:column;justify-content:space-between;gap:clamp(1px,.33cqw,5px);cursor:pointer;overflow:hidden;position:relative}",
  ".screen-tile:hover{border-color:#67dbbaaa}.screen-tile.selected{border-color:#5adad5;box-shadow:inset 0 0 0 1px #5adad5}",
  ".tile-label{flex:0 0 auto;font-size:clamp(8px,1.55cqw,20px);line-height:1.05;opacity:.75;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}",
  ".tile-value{flex:0 1 auto;font-size:clamp(11px,2.5cqw,34px);line-height:1.1;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}",
  ".tile-hint{flex:0 0 auto;font-size:clamp(7px,1.15cqw,15px);line-height:1;opacity:.65;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}",
  ".tile-icon{position:absolute;top:8%;right:6%;width:clamp(12px,2.2cqw,28px);height:clamp(12px,2.2cqw,28px);pointer-events:none}",
  ".tile-camera{flex:1;display:grid;place-items:center;text-align:center;min-height:0;margin:1cqw 0;background:#020911;border-radius:6px;font-size:clamp(8px,1.3cqw,16px);opacity:.9;position:relative;overflow:hidden}",
  ".camera-dot{position:absolute;top:7px;left:8px;font-size:clamp(7px,.95cqw,12px);color:#ff7171}",
  ".tile-graph{width:100%;height:36%;min-height:10px;flex:1 1 auto;overflow:hidden}.tile-graph polyline{fill:none;stroke:currentColor;stroke-width:3;vector-effect:non-scaling-stroke}",
  ".bar{flex:0 0 auto;height:clamp(2px,.45cqw,6px);background:#ffffff2c;border-radius:99px;overflow:hidden}.bar>i{display:block;height:100%;background:#67dbba;border-radius:inherit}",
  ".inspector{padding:17px 18px;border-left:1px solid var(--divider-color,#ddd);min-width:0}",
  ".inspector-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:14px}",
  ".inspector-head strong{font-size:15px}.field{display:grid;gap:5px;margin-bottom:12px;font-size:12px}",
  ".field input,.field select,.field textarea{width:100%;font-size:13px}.field textarea{min-height:180px;resize:vertical;font-family:ui-monospace,monospace}",
  ".hint{font-size:12px;line-height:1.5;opacity:.65}.actions{display:flex;gap:7px;flex-wrap:wrap;margin-top:12px}",
  ".theme-gallery{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-bottom:16px}",
  ".theme-choice{display:flex;flex-direction:column;gap:6px;align-items:stretch;padding:7px;text-align:left;min-width:0}",
  ".theme-choice.active{outline:2px solid var(--primary-color,#327dc5);outline-offset:1px}",
  ".theme-sample{display:flex;align-items:center;gap:7px;height:50px;border-radius:8px;padding:8px;background:var(--sample-bg,#10151e);color:var(--sample-text,#fff)}",
  ".theme-sample i{display:block;width:27px;height:28px;border-radius:7px;background:var(--sample-card,#1b2432);border-left:5px solid var(--sample-accent,#67dbba)}",
  ".theme-choice span{font-size:12px;font-weight:600}",
  ".tile-style-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.tile-style-grid input{min-width:0;padding:1px;height:36px}",
  ".layout-gallery{display:grid;gap:8px;margin:14px 0}",
  ".layout-choice{padding:12px;border:1px solid var(--divider-color,#ddd);border-radius:10px}",
  ".layout-choice strong{font-size:13px}.layout-choice .actions{margin-top:8px}",
  ".layout-thumb{display:grid;gap:4px;width:95px;height:45px;margin-top:8px}",
  ".layout-thumb i{background:var(--primary-color,#327dc5);opacity:.35;border-radius:3px}",
  ".section-hint{font-size:11px;opacity:.72;margin:8px 0}",
  ".pagebar{display:flex;gap:5px;flex-wrap:wrap;margin-bottom:11px}",
  ".page{font-size:12px;padding:6px 11px}.page.active{background:var(--primary-color,#327dc5);color:#fff}",
  ".footer{display:flex;gap:10px;align-items:center;justify-content:space-between;flex-wrap:wrap;padding:12px 17px;border-top:1px solid var(--divider-color,#ddd)}",
  ".empty{padding:30px 18px;text-align:center;font-size:14px;opacity:.7}",
  "datalist{display:none}@media(max-width:850px){.content{grid-template-columns:1fr}.inspector{border-left:0;border-top:1px solid var(--divider-color,#ddd)}}"
].join("\n");

const clone = (value) => JSON.parse(JSON.stringify(value));
const esc = (value) => String(value == null ? "" : value).replace(/[&<>"']/g,
  (char) => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[char]));
const option = (value, label, chosen) => "<option value='" + esc(value) + "'" +
  (String(value) === String(chosen) ? " selected" : "") + ">" + esc(label) + "</option>";
const attr = (object) => Object.entries(object).map(([key, val]) => key + ":" + val).join(";");

function nativeCss(css) {
  const result = {};
  const vars = {};
  for (const match of String(css || "").replace(/\/\*[\s\S]*?\*\//g, "").matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selectors = match[1].split(",").map((item) => item.trim());
    const declarations = match[2].split(";");
    for (const item of declarations) {
      const split = item.indexOf(":");
      if (split < 0) continue;
      const key = item.slice(0, split).trim().toLowerCase();
      let val = item.slice(split + 1).trim().toLowerCase();
      if (selectors.includes(":root") && key.startsWith("--") && /^#(?:[0-9a-f]{6}|[0-9a-f]{3})$/.test(val)) {
        vars[key] = val; continue;
      }
      const variable = /^var\((--[a-z0-9-]+)\)$/.exec(val);
      if (variable) val = vars[variable[1]] || "";
      const valid = (["background-color", "color"].includes(key) && /^#(?:[0-9a-f]{6}|[0-9a-f]{3})$/.test(val)) ||
        (["font-size", "border-radius"].includes(key) && /^\d{1,2}px$/.test(val)) ||
        (key === "font-weight" && ["normal","bold"].includes(val));
      if (!valid) continue;
      for (const selector of selectors) {
        if (!selector.startsWith("." ) && selector !== "body") continue;
        (result[selector] ||= {})[key] = val;
      }
    }
  }
  return result;
}
function combine(rules, ...names) {
  const combined = {};
  for (const name of names) Object.assign(combined, rules[name] || {});
  return attr(combined);
}

class HaFrameVisualEditor extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({mode: "open"});
    this._configCard = {};
    this._frames = [];
    this._frameId = "";
    this._config = null;
    this._revision = "";
    this._page = 0;
    this._tile = -1;
    this._panel = "tile";
    this._dirty = false;
    this._loading = false;
    this._busy = false;
    this._notice = "";
    this._error = false;
    this._drag = -1;
    this.shadowRoot.addEventListener("click", (event) => this._click(event));
    this.shadowRoot.addEventListener("input", (event) => this._input(event));
    this.shadowRoot.addEventListener("change", (event) => this._change(event));
    this.shadowRoot.addEventListener("dragstart", (event) => {
      const tile = event.target.closest("[data-tile]");
      if (!tile) return;
      this._drag = Number(tile.dataset.tile);
      if (event.dataTransfer) event.dataTransfer.setData("text/plain", String(this._drag));
    });
    this.shadowRoot.addEventListener("dragover", (event) => {
      if (event.target.closest("[data-tile]")) event.preventDefault();
    });
    this.shadowRoot.addEventListener("drop", (event) => {
      const tile = event.target.closest("[data-tile]");
      if (!tile || this._drag < 0) return;
      event.preventDefault();
      this._reorder(this._drag, Number(tile.dataset.tile));
      this._drag = -1;
    });
  }

  setConfig(value) {
    this._configCard = value || {};
  }
  static getStubConfig() { return {type: "custom:ha-frame-visual-editor"}; }
  getCardSize() { return 9; }
  getGridOptions() { return {columns: 12, rows: 8, min_rows: 5}; }
  set hass(value) {
    this._hass = value;
    if (this.isConnected && !this._frames.length && !this._loading) this._bootstrap();
    else if (this._config) this._preview();
  }
  connectedCallback() {
    if (this._hass && !this._frames.length && !this._loading) this._bootstrap();
  }
  _message(text, error) {
    this._notice = String(text || "");
    this._error = Boolean(error);
    const node = this.shadowRoot.querySelector("[data-notice]");
    if (node) { node.textContent = this._notice; node.className = "status" + (this._error ? " error" : ""); }
  }
  _mark() {
    this._dirty = true;
    this._message("Modifications non enregistrées", false);
    const save = this.shadowRoot.querySelector("[data-action='save']");
    if (save) save.disabled = false;
  }
  async _bootstrap() {
    if (this._loading || !this._hass) return;
    this._loading = true;
    this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card><div class='empty'>Chargement des cadres Home Assistant…</div></ha-card>";
    try {
      const response = await this._hass.callWS({type: "ha_frame/editor/list"});
      this._frames = response.frames || [];
      if (this._frames.length) {
        const preferred = this._configCard.entry_id || this._configCard.frame_id;
        this._frameId = this._frames.some((frame) => frame.entry_id === preferred) ? preferred : this._frames[0].entry_id;
        await this._open();
      } else {
        this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card><div class='empty'>Aucun cadre jumelé. Ajoute HA Frame PF1007 dans Appareils et services.</div></ha-card>";
      }
    } catch (error) {
      this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card><div class='empty'>Impossible d’ouvrir l’éditeur : " + esc(error.message || error) + "</div></ha-card>";
    } finally { this._loading = false; }
  }
  async _open() {
    this._config = null;
    this._revision = "";
    this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card><div class='empty'>Chargement de la configuration du cadre…</div></ha-card>";
    try {
      const response = await this._hass.callWS({type:"ha_frame/editor/get", entry_id:this._frameId});
      this._config = clone(response.config);
      this._revision = response.revision;
      this._page = 0;
      this._tile = -1;
      this._panel = "tile";
      this._dirty = false;
      this._message("", false);
      this._render();
    } catch (error) {
      this._message("Impossible de charger ce cadre : " + (error.message || error), true);
      this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card><div class='top'><div class='heading'>Cadre indisponible</div><div class='row toolbar'><select data-control='frame'>" +
        this._frameOptions() + "</select><button data-action='reload'>Réessayer</button></div></div><div class='empty'>" +
        esc(this._notice) + "</div></ha-card>";
    }
  }
  _pageObject() { return this._config.pages[this._page]; }
  _tileObject() { return this._tile < 0 ? null : this._pageObject().cards[this._tile]; }
  _frameOptions() {
    return this._frames.map((f) => option(f.entry_id, f.name + (f.online ? "" : " (hors ligne)"), this._frameId)).join("");
  }
  _render() {
    const config = this._config;
    if (!config) return;
    const page = this._pageObject();
    const buttons = config.pages.map((p,i) => "<button class='page" + (i===this._page?" active":"") +
      "' data-page='" + i + "'>" + esc(p.title) + "</button>").join("");
    const cloneOptions = this._frames.filter(f => f.entry_id !== this._frameId)
      .map(f => option(f.entry_id, f.name, "")).join("");
    this.shadowRoot.innerHTML = "<style>" + STYLE + "</style><ha-card>" +
      "<div class='top'><div class='brand'><div><div class='heading'>HA Frame · Studio</div>" +
      "<div class='sub'>Éditeur visuel multi-cadres · aperçu 1280 × 800</div></div>" +
      "<div class='row'><select aria-label='Choisir un cadre' class='frame-select' data-control='frame'>" +
      this._frameOptions() + "</select><button data-action='reload' title='Relire la configuration du cadre'>↻ Actualiser</button></div></div>" +
      "<div class='row toolbar'>" +
      "<button data-action='appearance'>Thèmes</button><button data-action='css'>CSS personnalisé</button>" +
      "<button data-action='page-panel'>Page</button><button data-action='layouts'>Dispositions</button>" +
      "<button data-action='add-tile'>+ Tuile</button>" +
      "<span class='spacer'></span><label>" + esc(page.cards.length) + "/" + esc(page.columns*3) + " tuiles</label></div></div>" +
      "<div class='content'><div class='canvas-area'>" +
      "<div class='canvas-head'><strong>Aperçu du cadre</strong><span class='muted'>Clique une tuile pour la modifier · glisse pour déplacer</span></div>" +
      "<div class='pagebar'>" + buttons + "<button data-action='add-page' class='page' title='Ajouter une page'>+ Page</button></div>" +
      "<div class='screen-wrap'><div class='screen' data-preview></div></div>" +
      "<div class='hint' style='margin-top:9px'>Simulation du rendu Canvas Android. Les états affichés viennent de Home Assistant ; aucun appareil n’est actionné depuis cet aperçu.</div></div>" +
      "<aside class='inspector' data-inspector></aside></div>" +
      "<div class='footer'><div class='row'>" +
      (cloneOptions ? "<label class='muted'>Copier la config d’un autre cadre :</label><select data-control='source'>" + cloneOptions + "</select><button class='btn-small' data-action='clone'>Copier</button>" : "<span class='muted'>Chaque cadre conserve sa configuration indépendante.</span>") +
      "</div><div class='row'><span class='status" + (this._error ? " error" : "") + "' data-notice>" +
      esc(this._notice || (this._dirty ? "Modifications non enregistrées" : "Synchronisé avec le cadre")) +
      "</span><button data-action='discard'>Annuler</button><button class='primary' data-action='save'" +
      (!this._dirty ? " disabled" : "") + ">Enregistrer sur ce cadre</button></div></div></ha-card>";
    this._preview();
    this._inspector();
  }
  _preview() {
    if (!this._config) return;
    const target = this.shadowRoot.querySelector("[data-preview]");
    if (!target) return;
    const config = this._config, page = this._pageObject();
    const rules = nativeCss(config.custom_css);
    const scale = Math.max(.2, Math.min(1.2, (target.clientWidth || 640) / 1280));
    const css = (...selectors) => previewCss(rules, scale, ...selectors);
    let layout;
    try { layout = gridPlacement(page); }
    catch (error) { this._message(error.message || String(error), true); return; }
    const states = this._hass && this._hass.states || {};
    this._graphSamples ||= {};
    const activeTabs = config.pages.map((p,i) => "<div class='screen-tab" +
      (i===this._page ? " active" : "") + "' style='" +
      css(".page-tab", i===this._page ? ".page-tab.active" : "") + "'>" +
      esc(p.title) + "</div>").join("");
    const grid = page.cards.map((tile,i) => {
      const entity = states[tile.entity_id], raw = entity && entity.state || "—";
      const attrs = entity && entity.attributes || {};
      const isOn = ["on","open","heat","cool","playing"].includes(raw);
      let value = raw === "—" ? "—" : raw;
      let hint = tile.entity_id || "Non configurée", percent = null, graph = "";
      if (tile.type === "metric" || tile.type === "gauge") {
        value = raw === "—" ? "—" : raw + (attrs.unit_of_measurement ? " " + attrs.unit_of_measurement : "");
        if (tile.type === "gauge") {
          const n = Number(raw), low = Number(tile.min || 0), high = Number(tile.max == null ? 100 : tile.max);
          percent = raw !== "—" && Number.isFinite(n) && high>low ? Math.max(0,Math.min(100,(n-low)*100/(high-low))) : 0;
        }
      } else if (tile.type === "thermostat") {
        value = attrs.current_temperature != null ? attrs.current_temperature + " °C" : raw;
        hint = attrs.temperature != null ? "Consigne " + attrs.temperature + " °C" : hint;
      } else if (tile.type === "light") {
        value = raw === "on" ? "Allumée" : raw === "off" ? "Éteinte" : raw;
        percent = raw === "on" && attrs.brightness != null ? Math.round(attrs.brightness * 100 / 255) : (isOn ? 100 : 0);
      } else if (tile.type === "cover") {
        value = raw === "open" ? "Ouvert" : raw === "closed" ? "Fermé" : raw;
        percent = attrs.current_position == null ? null : Number(attrs.current_position);
      } else if (tile.type === "control") {
        value = isOn ? "Activé" : raw === "off" ? "Désactivé" : raw;
      } else if (tile.type === "weather") {
        value = attrs.temperature == null ? "—" : Math.round(Number(attrs.temperature)) + " °C";
        const conditions = {sunny:"Ensoleillé",cloudy:"Nuageux",partlycloudy:"Éclaircies",
          rainy:"Pluie",pouring:"Fortes pluies",snowy:"Neige","clear-night":"Ciel dégagé",
          lightning:"Orage",fog:"Brouillard",windy:"Vent"};
        hint = (conditions[raw] || raw) + (attrs.humidity != null ? " · " + attrs.humidity + "% HR" : "");
      } else if (tile.type === "graph") {
        value = raw === "—" ? "—" : raw + (attrs.unit_of_measurement ? " " + attrs.unit_of_measurement : "");
        const n = Number(raw), key = this._frameId + "|" + tile.entity_id;
        const series = this._graphSamples[key] ||= [];
        if (tile.entity_id && raw !== "—" && Number.isFinite(n) &&
            (series.length === 0 || series[series.length-1] !== n)) {
          series.push(n);
          if (series.length > 24) series.shift();
        }
        if (series.length >= 2) {
          const low = Math.min(...series), high = Math.max(...series);
          const range = Math.max(1e-6,high-low);
          const points = series.map((n,j)=> (j*100/(series.length-1)).toFixed(1) +
            "," + (90-(n-low)*80/range).toFixed(1)).join(" ");
          graph = "<svg class='tile-graph' viewBox='0 0 100 100' preserveAspectRatio='none' " +
            "aria-label='Tendance des dernières valeurs reçues'><polyline points='" + points + "'/></svg>";
          hint = series.length + " valeurs depuis l'ouverture de Studio";
        } else {
          hint = "Collecte de la tendance sur le cadre…";
        }
      } else if (tile.type === "camera") {
        value = "Caméra";
        hint = tile.entity_id ? "Flux MJPEG depuis Home Assistant · 1 image/s" : "Choisir une entité camera.*";
      }
      const slot = tile.type === "metric"
        ? ".metric-" + page.cards.slice(0,i+1).filter(t=>t.type==="metric").length
        : ".control-" + page.cards.slice(0,i+1).filter(t=>t.type!=="metric").length;
      const individual = tile.style || {};
      const tileCss = css(".card", "." + tile.type, slot, isOn ? ".control.on" : "");
      const labelCss = css(".metric-label", "." + tile.type, slot);
      const valueCss = css(".metric-value", "." + tile.type, slot);
      const custom = [
        individual.background ? "background-color:" + individual.background : "",
        individual.foreground ? "color:" + individual.foreground : "",
        Number.isInteger(individual.radius) ? "border-radius:" + individual.radius*scale + "px" : ""
      ].filter(Boolean).join(";");
      const labelColor = individual.foreground ? "color:" + individual.foreground : "";
      const accent = individual.accent || "#67dbba";
      const bar = percent == null ? "" : "<div class='bar'><i style='width:" +
        Math.max(0,Math.min(100,Number(percent)||0)) + "%;background:" + accent + "'></i></div>";
      const cell = layout.cells[i];
      const icon = tileIconMarkup(tile.icon || "", tile.type, accent);
      const camera = tile.type === "camera" ? "<div class='tile-camera' aria-label='Flux vidéo' >" +
        "<span class='camera-dot'>● LIVE</span><span>Aperçu MJPEG sur le cadre</span></div>" : "";
      return "<div class='screen-tile" + (this._tile===i?" selected":"") +
        "' data-tile='" + i + "' role='button' tabindex='0' draggable='true' style='" +
        tileCss + ";" + custom + ";grid-column:" + (cell.col+1) + " / span " + cell.w +
        ";grid-row:" + (cell.row+1) + " / span " + cell.h + "'>" +
        icon + "<div class='tile-label' style='" + labelCss + ";" + labelColor + "'>" +
        esc(tile.label || tile.type) + "</div>" +
        (tile.type === "camera" ? camera : "<div class='tile-value' style='" + valueCss + ";" + labelColor + "'>" +
        esc(value) + "</div>") +
        (graph ? graph.replace("<svg class=", "<svg style='color:" + accent + "' class=") : "") + bar +
        "<div class='tile-hint' style='" + labelColor + "'>" + esc(hint) + "</div></div>";
    }).join("");
    const date = new Intl.DateTimeFormat("fr-FR", {weekday:"long",day:"numeric",month:"long"}).format(new Date());
    const clock = new Intl.DateTimeFormat("fr-FR",{hour:"2-digit",minute:"2-digit"}).format(new Date());
    const frame = this._frames.find(f=>f.entry_id===this._frameId);
    const rowPct = Math.min(183/472*100, (472-(layout.rows-1)*15)/layout.rows/472*100);
    target.setAttribute("style", css("body"));
    target.innerHTML = "<div class='screen-inner'>" +
      "<div class='screen-header'><div><div class='screen-title' style='" + css(".header") +
      "'>" + esc(config.dashboard_title) + "</div><div class='screen-sub' style='" +
      css(".subtitle") + "'>Tableau de bord</div></div><div class='screen-online' style='" +
      css(".status") + "'>" + (frame && frame.online ? "● Connecté" : "○ Déconnecté") + "</div></div>" +
      "<div class='screen-clock' style='" + css(".clock") + "'>" + esc(clock) +
      "<span class='screen-date' style='" + css(".date") + "'>" + esc(date) + "</span></div>" +
      "<div class='screen-tabs'>" + activeTabs + "</div>" +
      "<div class='screen-grid' style='grid-template-columns:repeat(" + page.columns +
      ",minmax(0,1fr));grid-template-rows:repeat(" + layout.rows + "," +
      rowPct.toFixed(3) + "%)'>" + grid + "</div></div>";
  }
  _inspector() {
    const target = this.shadowRoot.querySelector("[data-inspector]");
    if (!target || !this._config) return;
    const page = this._pageObject(), tile = this._tileObject();
    let html = "";
    if (this._panel === "css") {
      html = "<div class='inspector-head'><strong>CSS personnalisé</strong></div>" +
        "<div class='field'><label>CSS Canvas</label><textarea data-bind='css' spellcheck='false'>" +
        esc(this._config.custom_css) + "</textarea></div>" +
        "<p class='hint'>Couleurs, tailles, poids et arrondis sont prévisualisés. Le rendu Android reste la référence et valide le CSS à l’enregistrement.</p>";
    } else if (this._panel === "appearance") {
      const selectedCss = removeDesign(this._config.custom_css);
      const selected = Object.entries(PRESETS).find(([,css]) => css === selectedCss);
      const selectedId = selected ? selected[0] : "custom";
      const gallery = Object.keys(PRESETS).map((id) => {
        const p = THEME_SWATCHES[id] || PALETTE_THEMES[id];
        const active = selectedId === id;
        return "<button type='button' class='theme-choice" + (active ? " active" : "") +
          "' data-preset='" + esc(id) + "' aria-pressed='" + active + "'>" +
          "<div class='theme-sample' style='--sample-bg:" + p.bg + ";--sample-card:" +
          p.card + ";--sample-text:" + p.text + ";--sample-accent:" + p.accent +
          "'><i></i><i></i><i></i></div><span>" + esc(THEME_LABELS[id]) + "</span></button>";
      }).join("");
      html = "<div class='inspector-head'><strong>Galerie de thèmes</strong></div>" +
        "<label class='field'>Titre du cadre<input data-bind='title' maxlength='28' value='" +
        esc(this._config.dashboard_title) + "'></label>" +
        "<p class='section-hint'>Choisis un thème pour prévisualiser les couleurs avant d’enregistrer.</p>" +
        "<div class='theme-gallery'>" + gallery + "</div>" +
        (selectedId === "custom" ? "<p class='section-hint'>CSS personnalisé actuellement actif.</p>" : "") +
        "<label class='field'>Design des tuiles<select data-bind='shape'>" +
        Object.entries(SHAPES).map(([id,p])=>option(id,p.label,readShape(this._config.custom_css))).join("") +
        "</select></label>" +
        "<label class='field'>Typographie<select data-bind='typography'>" +
        Object.entries(TYPOGRAPHY).map(([id,p])=>option(id,p.label,readTypography(this._config.custom_css))).join("") +
        "</select></label>" +
        "<p class='hint'>Les formes ajoutent des règles CSS compatibles avec Android Canvas. Un nouveau thème remplace le CSS précédent après confirmation.</p>";
    } else if (this._panel === "layouts") {
      html = "<div class='inspector-head'><strong>Modèles de disposition</strong></div>" +
        "<p class='hint'>Ajoute une page ou remplace la page actuelle. Les tuiles sont pré-remplies sans entités : associe-les ensuite à Home Assistant.</p>" +
        "<div class='layout-gallery'>" + LAYOUT_TEMPLATES.map(t => {
          const thumb = "<div class='layout-thumb' style='grid-template-columns:repeat(" + t.columns +
            ",1fr)'>" + t.tiles.map(()=>"<i></i>").join("") + "</div>";
          return "<div class='layout-choice'><strong>" + esc(t.name) + "</strong>" +
            "<div class='muted'>" + t.columns + " colonnes · " + t.tiles.length + " tuiles</div>" +
            thumb + "<div class='actions'><button class='btn-small' data-layout='" + t.id +
            "' data-mode='add'>+ Page</button><button class='btn-small' data-layout='" + t.id +
            "' data-mode='replace'>Remplacer</button></div></div>";
        }).join("") + "</div>";
    } else if (this._panel === "page" || !tile) {
      html = "<div class='inspector-head'><strong>Page " + esc(this._page + 1) + "</strong></div>" +
        "<label class='field'>Nom<input data-bind='page-title' maxlength='32' value='" + esc(page.title) + "'></label>" +
        "<label class='field'>Colonnes<select data-bind='columns'>" + [2,3,4].map(n=>option(n,n+" colonnes",page.columns)).join("") +
        "</select></label><p class='hint'>Entre 1 et 4 pages, jusqu’à 3 rangées par page. Les cartes peuvent être déplacées en les faisant glisser.</p>" +
        "<div class='actions'><button data-action='page-left'>← Page</button><button data-action='page-right'>Page →</button>" +
        "<button data-action='duplicate-page'>Dupliquer page</button>" +
        "<button data-action='delete-page' class='danger'>Supprimer page</button></div>";
    } else {
      const styleValues = tile.style || {};
      const radius = Number.isInteger(styleValues.radius) ? styleValues.radius : "";
      const appearance = [
        ["background","Fond", "#1b2432"],
        ["foreground","Texte", "#f5f7fb"],
        ["accent","Accent", "#67dbba"]
      ].map(([key,label,fallback]) =>
        "<label class='field'>" + label + " <input data-bind='style-" + key +
        "' type='color' value='" + esc(styleValues[key] || fallback) +
        "'></label>").join("");
      html = "<div class='inspector-head'><strong>Tuile " + (this._tile + 1) + "</strong>" +
        "<button class='btn-small' data-action='page-panel'>Éditer la page</button></div>" +
        "<label class='field'>Type<select data-bind='card-type'>" +
        TYPES.map(([type,name])=>option(type,name,tile.type)).join("") + "</select></label>" +
        "<label class='field'>Nom de la tuile<input data-bind='card-label' maxlength='48' value='" +
        esc(tile.label) + "'></label>" +
        "<label class='field'>Entité Home Assistant<input data-bind='card-entity' list='ha-frame-entities' placeholder='" +
        (tile.type==="camera" ? "camera.salon" : tile.type==="weather" ? "weather.maison" : "sensor.temperature") +
        "' value='" + esc(tile.entity_id) + "'><datalist id='ha-frame-entities'>" +
        Object.keys(this._hass && this._hass.states || {}).slice(0,2000).map(id=>"<option value='" + esc(id) +
        "'></option>").join("") + "</datalist></label>" +
        (tile.type==="camera" ? "<p class='hint'>Flux MJPEG via Home Assistant, avec repli JPEG (≈ 1 image/s). Un seul flux actif sur le cadre, sans audio. APK 1.0.0 requis.</p>" : "") +
        (tile.type==="graph" ? "<p class='hint'>Courbe des 24 derniers changements de valeur depuis la connexion du cadre ; sans historique permanent.</p>" : "") +
        (tile.type==="weather" ? "<p class='hint'>Utilise une entité météo Home Assistant : weather.*</p>" : "") +
        (tile.type === "gauge" ? "<div class='row'><label class='field' style='flex:1'>Minimum<input data-bind='card-min' type='number' value='" +
          esc(tile.min) + "'></label><label class='field' style='flex:1'>Maximum<input data-bind='card-max' type='number' value='" +
          esc(tile.max) + "'></label></div>" : "") +
        "<label class='field'>Icône<select data-bind='card-icon'>" +
        ICON_OPTIONS.map(([id,name])=>option(id,name,tile.icon || "")).join("") + "</select></label>" +
        "<div class='row'><label class='field' style='flex:1'>Largeur<select data-bind='card-colspan'>" +
        Array.from({length:page.columns},(_,i)=>option(i+1,(i+1)+" colonne"+(i?"s":""),tile.col_span||1)).join("") +
        "</select></label><label class='field' style='flex:1'>Hauteur<select data-bind='card-rowspan'>" +
        [1,2].map(n=>option(n,n+" rangée"+(n>1?"s":""),tile.row_span||1)).join("") + "</select></label></div>" +
        "<div class='inspector-head'><strong>Apparence individuelle</strong></div>" +
        "<div class='tile-style-grid'>" + appearance + "</div>" +
        "<label class='field'>Rayon des angles (px ; vide = thème)<input data-bind='style-radius' type='number' min='0' max='72' placeholder='Par défaut' value='" +
        esc(radius) + "'></label>" +
        "<div class='actions'><button data-action='clear-tile-style'>Réinitialiser le style</button></div>" +
        "<div class='actions'><button data-action='tile-left'>← Avant</button><button data-action='tile-right'>Après →</button>" +
        "<button data-action='duplicate-tile'>Dupliquer</button>" +
        "<button data-action='delete-tile' class='danger'>Supprimer</button></div>";
    }
    target.innerHTML = html;
  }
  _click(event) {
    const tile = event.target.closest("[data-tile]");
    if (tile) {
      this._tile = Number(tile.dataset.tile);
      this._panel = "tile";
      this._render(); return;
    }
    const page = event.target.closest("[data-page]");
    if (page) {
      this._page = Number(page.dataset.page);
      this._tile = -1;
      this._panel = "page";
      this._render(); return;
    }
    const theme = event.target.closest("[data-preset]");
    if (theme) { this._applyPreset(theme.dataset.preset); return; }
    const layout = event.target.closest("[data-layout]");
    if (layout) { this._applyLayout(layout.dataset.layout, layout.dataset.mode); return; }
    const button = event.target.closest("[data-action]");
    if (button && !button.disabled) this._action(button.dataset.action);
  }
  _change(event) {
    const target = event.target;
    if (target.dataset.control === "frame") {
      const next = target.value, previous = this._frameId;
      if (this._dirty && !confirm("Abandonner les modifications non enregistrées ?")) {
        target.value = previous; return;
      }
      this._frameId = next;
      this._open();
      return;
    }
    if (["shape","typography"].includes(target.dataset.bind) && this._config) {
      const next = target.dataset.bind === "shape"
        ? withShape(this._config.custom_css, target.value)
        : withTypography(this._config.custom_css, target.value);
      if (next.length > 12000) {
        this._message("CSS trop long pour modifier la forme", true); return;
      }
      if (next !== this._config.custom_css) {
        this._config.custom_css = next;
        this._mark();
        this._render();
      }
      return;
    }
    if (target.dataset.bind === "columns" || target.dataset.bind === "card-type") {
      this._input(event);
      this._inspector();
    }
  }
  _input(event) {
    const target = event.target, bind = target.dataset.bind;
    if (!bind || bind === "preset" || !this._config) return;
    const page = this._pageObject(), tile = this._tileObject();
    if (bind === "title") this._config.dashboard_title = target.value;
    else if (bind === "css") this._config.custom_css = target.value;
    else if (bind === "page-title") page.title = target.value;
    else if (bind === "columns") {
      const cols = Number(target.value);
      if (page.cards.length > cols * 3) {
        this._message("Retire des tuiles avant de réduire les colonnes", true);
        target.value = page.columns; return;
      }
      page.columns = cols;
    } else if (tile) {
      if (bind === "card-type") tile.type = target.value;
      if (bind === "card-label") tile.label = target.value;
      if (bind === "card-entity") tile.entity_id = target.value.trim();
      if (bind === "card-min") tile.min = Number(target.value);
      if (bind === "card-max") tile.max = Number(target.value);
    }
    this._mark();
    this._preview();
  }
  _applyPreset(id) {
    if (!Object.prototype.hasOwnProperty.call(PRESETS, id)) return;
    const next = PRESETS[id];
    if (!this._config || this._config.custom_css === next) return;
    if (this._config.custom_css && !confirm("Remplacer le CSS actuel par ce thème ?")) return;
    this._config.custom_css = next;
    this._mark();
    this._render();
  }
  _applyLayout(id, mode) {
    if (!this._config) return;
    const template = LAYOUT_TEMPLATES.find(t=>t.id === id);
    if (!template || !["add","replace"].includes(mode)) return;
    const next = templatePage(template);
    if (mode === "add") {
      if (this._config.pages.length >= 4) return this._message("4 pages maximum", true);
      this._config.pages.push(next);
      this._page = this._config.pages.length - 1;
    } else {
      if (this._pageObject().cards.length &&
          !confirm("Remplacer la page et supprimer ses tuiles et leurs entités associées ?")) return;
      this._config.pages[this._page] = next;
    }
    this._tile = -1;
    this._panel = "page";
    this._mark();
    this._render();
  }
  _reorder(from,to) {
    if (from === to || from < 0 || to < 0) return;
    const cards = this._pageObject().cards;
    if (!cards[from] || !cards[to]) return;
    cards.splice(to, 0, cards.splice(from, 1)[0]);
    this._tile = to;
    this._mark();
    this._render();
  }
  _validate() {
    const cfg = this._config;
    if (!cfg.dashboard_title.trim() || cfg.dashboard_title.length > 28) return "Titre du dashboard : 1 à 28 caractères";
    if (cfg.custom_css.length > 12000) return "CSS : 12 000 caractères maximum";
    if (!cfg.pages.length || cfg.pages.length > 4) return "1 à 4 pages requises";
    for (const page of cfg.pages) {
      if (!page.title.trim() || page.title.length > 32) return "Titre de page invalide";
      if (![2,3,4].includes(page.columns) || page.cards.length > page.columns * 3) return "Trop de tuiles pour la grille";
      for (const tile of page.cards) {
        if (!TYPES.some(([type])=>type===tile.type)) return "Type de tuile invalide";
        if (tile.label.length > 48) return "Titre de tuile trop long";
        if (tile.entity_id && !/^[a-z0-9_]+\.[a-z0-9_]+$/.test(tile.entity_id)) return "Entité invalide : " + tile.entity_id;
        if (!Number.isFinite(Number(tile.min)) || !Number.isFinite(Number(tile.max)) ||
            Number(tile.max) <= Number(tile.min)) return "Plage de jauge invalide";
      }
    }
    return "";
  }
  async _save() {
    if (!this._dirty || this._busy) return;
    const error = this._validate();
    if (error) {this._message(error,true);return;}
    this._busy = true;
    this._message("Enregistrement sur le cadre…",false);
    try {
      const response = await this._hass.callWS({
        type: "ha_frame/editor/save", entry_id: this._frameId,
        revision: this._revision, config: clone(this._config)
      });
      this._revision = response.revision;
      this._dirty = false;
      this._message("Enregistré sur le cadre sélectionné", false);
      this._render();
    } catch (error) {
      this._message("Enregistrement impossible : " + (error.message || error), true);
    } finally {this._busy = false;}
  }
  async _action(action) {
    if (!this._config) {
      if (action === "reload") return this._open();
      return;
    }
    const page = this._pageObject(), tile = this._tileObject();
    if (action === "save") return this._save();
    if (action === "reload" || action === "discard") {
      if (this._dirty && !confirm("Abandonner les modifications non enregistrées ?")) return;
      return this._open();
    }
    if (action === "appearance" || action === "css" || action === "layouts") {
      this._panel = action; this._tile = -1; this._render(); return;
    }
    if (action === "page-panel") {this._panel="page";this._tile=-1;this._render();return;}
    if (action === "add-page") {
      if (this._config.pages.length >= 4) return this._message("4 pages maximum",true);
      this._config.pages.push({title:"Page "+(this._config.pages.length+1),columns:3,cards:[]});
      this._page=this._config.pages.length-1;this._tile=-1;this._panel="page";
    } else if (action === "duplicate-page") {
      if (this._config.pages.length >= 4) return this._message("4 pages maximum", true);
      const copied = clone(page);
      copied.title = ("Copie " + copied.title).slice(0, 32);
      this._config.pages.splice(this._page + 1, 0, copied);
      this._page++;
      this._tile = -1;
      this._panel = "page";
    } else if (action === "delete-page") {
      if (this._config.pages.length < 2) return this._message("Conserve au moins une page",true);
      if (!confirm("Supprimer cette page et toutes ses tuiles ?")) return;
      this._config.pages.splice(this._page,1);
      this._page=Math.max(0,this._page-1);this._tile=-1;
    } else if (action === "page-left" || action === "page-right") {
      const next=this._page+(action==="page-left"?-1:1);
      if (next < 0 || next >= this._config.pages.length) return;
      [this._config.pages[this._page],this._config.pages[next]] =
        [this._config.pages[next],this._config.pages[this._page]];this._page=next;
    } else if (action === "add-tile") {
      if (page.cards.length>=page.columns*3) return this._message("Limite de tuiles atteinte",true);
      page.cards.push({type:"metric",label:"Nouvelle tuile",entity_id:"",min:0,max:100});
      this._tile=page.cards.length-1;this._panel="tile";
    } else if (action === "delete-tile") {
      if (!tile || !confirm("Supprimer cette tuile ?")) return;
      page.cards.splice(this._tile,1);this._tile=-1;this._panel="page";
    } else if (action === "duplicate-tile") {
      if (!tile || page.cards.length>=page.columns*3) return;
      page.cards.splice(this._tile+1,0,clone(tile));this._tile++;
    } else if (action === "tile-left" || action === "tile-right") {
      if (!tile) return;
      return this._reorder(this._tile,this._tile+(action==="tile-left"?-1:1));
    } else if (action === "clone") {
      const source=this.shadowRoot.querySelector("[data-control='source']");
      if (!source || !source.value || !confirm("Copier la configuration de ce cadre dans l’éditeur ? Rien n’est enregistré avant de cliquer sur Enregistrer.")) return;
      try {
        const result=await this._hass.callWS({type:"ha_frame/editor/get",entry_id:source.value});
        this._config=clone(result.config);this._page=0;this._tile=-1;this._panel="page";
      } catch (error) {this._message("Copie impossible : "+(error.message||error),true);return;}
    } else return;
    this._mark();this._render();
  }
}
if (!customElements.get("ha-frame-visual-editor")) customElements.define("ha-frame-visual-editor", HaFrameVisualEditor);
window.customCards = window.customCards || [];
if (!window.customCards.some((card)=>card.type==="ha-frame-visual-editor")) {
  window.customCards.push({
    type:"ha-frame-visual-editor",
    name:"HA Frame · Studio",
    description:"Aperçu et édition visuelle des tuiles, thèmes et pages pour plusieurs cadres PF1007.",
    preview:false
  });
}
