"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(
  path.join(__dirname, "../custom_components/ha_frame/frontend/ha-frame-editor.js"),
  "utf8"
);
const elements = new Map();
const scope = {
  HTMLElement: class {},
  customElements: {
    define: (key, value) => elements.set(key, value),
    get: (key) => elements.get(key)
  },
  window: {customCards: []},
  confirm: () => true,
  console
};
vm.runInNewContext(source, scope);
const Editor = elements.get("ha-frame-visual-editor");
assert.ok(Editor);

function makeEditor(cards = [
  {type:"metric", label:"Température", entity_id:"sensor.temperature", min:0, max:100},
  {type:"light", label:"Salon", entity_id:"light.salon", min:0, max:100}
], columns = 3) {
  const instance = Object.create(Editor.prototype);
  instance._config = {
    dashboard_title:"Maison", custom_css:"",
    pages:[{title:"Principale",columns,cards:JSON.parse(JSON.stringify(cards))}]
  };
  instance._frameId = "frame-1";
  instance._frames = [{entry_id:"frame-1",version:"1.1.1"}];
  instance._revision = "rev-1";
  instance._panel = "tile";
  instance._page = 0;
  instance._tile = 0;
  instance._busy = false;
  instance._styleClipboard = null;
  instance._history = [];
  instance._historyIndex = -1;
  instance._historyGroup = "";
  instance._historyGroupAt = 0;
  instance._notice = "";
  instance._render = () => {};
  instance._inspector = () => {};
  instance._message = (message, error) => {
    instance._notice = message;
    instance._error = Boolean(error);
  };
  const buttons = {
    "[data-action='undo']": {disabled:true},
    "[data-action='redo']": {disabled:true},
    "[data-action='save']": {disabled:true}
  };
  instance.shadowRoot = {
    innerHTML: "",
    querySelector: (selector) => buttons[selector] || null
  };
  instance._resetHistory();
  return {instance, buttons};
}

{
  const {instance,buttons} = makeEditor();
  instance._config.pages[0].cards[0].label = "Intérieur";
  instance._mark();
  assert.equal(instance._history.length, 2);
  assert.equal(instance._dirty, true);
  assert.equal(buttons["[data-action='undo']"].disabled,false);
  instance._stepHistory(-1);
  assert.equal(instance._config.pages[0].cards[0].label, "Température");
  assert.equal(instance._dirty, false);
  instance._stepHistory(1);
  assert.equal(instance._config.pages[0].cards[0].label, "Intérieur");
  assert.equal(instance._dirty, true);

  // New edit after undo drops the old redo branch.
  instance._stepHistory(-1);
  instance._config.pages[0].cards[0].label = "Garage";
  instance._mark();
  assert.equal(instance._history.length,2);
  assert.equal(instance._config.pages[0].cards[0].label,"Garage");
  instance._stepHistory(1);
  assert.equal(instance._config.pages[0].cards[0].label,"Garage");
  instance._mark();
  assert.equal(instance._history.length,2);
  instance._resetHistory();
  assert.equal(instance._history.length,1);
  assert.equal(instance._dirty,false);
}

{
  const {instance} = makeEditor();
  instance._config.dashboard_title="Mai";
  instance._mark("typing:title");
  instance._config.dashboard_title="Maison 2";
  instance._mark("typing:title");
  assert.equal(instance._history.length,2);
  instance._stepHistory(-1);
  assert.equal(instance._config.dashboard_title,"Maison");
  instance._stepHistory(1);
  assert.equal(instance._config.dashboard_title,"Maison 2");
  // Keep at most 50 edits and one baseline entry.
  for(let i=0;i<90;i++){
    instance._config.dashboard_title="Title "+i;
    instance._mark();
  }
  assert.equal(instance._history.length,51);
}

{
  const {instance} = makeEditor();
  let prevented=0;
  const target={closest:()=>null};
  instance._config.dashboard_title="Modifié";
  instance._mark();
  instance._shortcut({key:"z",ctrlKey:true,shiftKey:false,altKey:false,target,
    preventDefault(){prevented++;}});
  assert.equal(instance._config.dashboard_title,"Maison");
  instance._shortcut({key:"z",ctrlKey:true,shiftKey:true,altKey:false,target,
    preventDefault(){prevented++;}});
  assert.equal(instance._config.dashboard_title,"Modifié");
  instance._shortcut({key:"z",ctrlKey:true,shiftKey:false,altKey:false,target,
    preventDefault(){prevented++;}});
  instance._shortcut({key:"y",ctrlKey:true,shiftKey:false,altKey:false,target,
    preventDefault(){prevented++;}});
  assert.equal(instance._config.dashboard_title,"Modifié");
  assert.equal(prevented,4);
  instance._shortcut({key:"z",ctrlKey:true,shiftKey:false,altKey:false,
    target:{closest:()=>({tagName:"INPUT"})}, preventDefault(){prevented++;}});
  assert.equal(instance._config.dashboard_title,"Modifié");
  assert.equal(prevented,4);
}

{
  const {instance}=makeEditor();
  instance._resizeTile(0,"grow-width");
  assert.equal(instance._config.pages[0].cards[0].col_span,2);
  instance._resizeTile(0,"grow-height");
  assert.equal(instance._config.pages[0].cards[0].row_span,2);
  instance._stepHistory(-1);
  assert.equal(instance._config.pages[0].cards[0].row_span,undefined);
  instance._stepHistory(-1);
  assert.equal(instance._config.pages[0].cards[0].col_span,undefined);
  assert.equal(instance._dirty,false);
  // Rejected resize never dirties the config or adds a history step.
  const full=makeEditor(Array.from({length:6},(_,i)=>({
    type:"metric",label:"Sensor "+i,entity_id:"",min:0,max:100
  })),2).instance;
  full._resizeTile(0,"grow-width");
  assert.equal(full._config.pages[0].cards[0].col_span,undefined);
  assert.equal(full._history.length,1);
  assert.equal(full._dirty,false);
  assert.match(full._notice,/Pas assez/);

  let stopped=false,prevented=false,resized=false;
  const oldResize=instance._resizeTile;
  instance._resizeTile=()=>{resized=true;};
  instance._click({
    target:{closest:(query)=>query==="[data-resize]"?
      {dataset:{resizeIndex:"0",resize:"grow-width"},disabled:false}:null},
    preventDefault(){prevented=true;}, stopPropagation(){stopped=true;}
  });
  assert.ok(resized && stopped && prevented);
  instance._resizeTile=oldResize;
}

(async()=>{
  const {instance}=makeEditor([
    {type:"light",label:"Salon",entity_id:"light.salon",min:0,max:100,
      style:{background:"#123456",foreground:"#ffffff",radius:24}},
    {type:"metric",label:"Bureau",entity_id:"sensor.bureau",min:0,max:100}
  ]);
  await instance._action("copy-tile-style");
  instance._tile=1;
  await instance._action("paste-tile-style");
  assert.equal(instance._config.pages[0].cards[1].style.background,"#123456");
  assert.equal(instance._config.pages[0].cards[1].entity_id,"sensor.bureau");
  assert.equal(instance._config.pages[0].cards[1].type,"metric");
  instance._config.pages[0].cards[1].style.radius=6;
  assert.equal(instance._config.pages[0].cards[0].style.radius,24);
  instance._stepHistory(-1);
  assert.equal(instance._config.pages[0].cards[1].style,undefined);
  assert.equal(instance._dirty,false);

  instance._config.dashboard_title="New title";
  instance._mark();
  instance._prepareMdiConfig=async()=>JSON.parse(JSON.stringify(instance._config));
  let saves=0;
  instance._hass={callWS:async(msg)=>{
    assert.equal(msg.type,"ha_frame/editor/save");
    assert.equal(msg.entry_id,"frame-1");
    assert.equal(msg.revision,"rev-1");
    saves++;
    return {revision:"rev-2"};
  }};
  instance._validate=()=>"";
  await instance._save();
  assert.equal(saves,1);
  assert.equal(instance._revision,"rev-2");
  assert.equal(instance._dirty,false);
  assert.equal(instance._history.length,1);

  // Reloading a frame starts a new independent history.
  instance._config.dashboard_title="Unsaved change";
  instance._mark();
  instance._hass.callWS=async()=>({
    config:{dashboard_title:"Another frame",custom_css:"",
      pages:[{title:"Bedroom",columns:2,cards:[]}]}, revision:"rev-3"
  });
  instance._frameId="frame-2";
  await instance._open();
  assert.equal(instance._config.dashboard_title,"Another frame");
  assert.equal(instance._history.length,1);
  assert.equal(instance._dirty,false);

  // Two overlapping fetches must never let a stale frame overwrite the current one.
  const late=makeEditor().instance;
  const requests={};
  late._hass={callWS:({entry_id})=>new Promise(resolve=>{requests[entry_id]=resolve;})};
  late._frameId="frame-old";
  const firstLoad=late._open();
  late._frameId="frame-new";
  const secondLoad=late._open();
  requests["frame-new"]({revision:"new-r1",config:{
    dashboard_title:"New",custom_css:"",
    pages:[{title:"New page",columns:2,cards:[]}]
  }});
  await secondLoad;
  requests["frame-old"]({revision:"old-r1",config:{
    dashboard_title:"Stale",custom_css:"",
    pages:[{title:"Old page",columns:2,cards:[]}]
  }});
  await firstLoad;
  assert.equal(late._config.dashboard_title,"New");
  assert.equal(late._revision,"new-r1");
  assert.equal(late._history.length,1);

  late._busy=true;
  const frameSelect={dataset:{control:"frame"},value:"frame-old"};
  late._change({target:frameSelect});
  assert.equal(frameSelect.value,"frame-new");
  late._input({target:{dataset:{bind:"title"},value:"Should not apply"}});
  assert.equal(late._config.dashboard_title,"New");
  late._busy=false;

  assert.match(source,/data-action='undo'/);
  assert.match(source,/data-action='redo'/);
  assert.match(source,/data-resize='/);
  assert.match(source,/data-action='copy-tile-style'/);
  console.log("Studio v1.5 history, tile resize, clipboard and safe-save tests passed.");
})().catch(error=>{console.error(error);process.exitCode=1;});
