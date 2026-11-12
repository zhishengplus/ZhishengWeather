import {BadgeAwardFlow} from './award-flow.mjs';
import {BadgePreparationWatchdog} from './preparation-watchdog.mjs';
import {loadBadgeSceneResources} from './scene-resources.mjs';
import {GalleryFrameCache} from './gallery-frame-cache.mjs';
import {GalleryLayoutCache} from './gallery-layout-cache.mjs';
import * as THREE from './vendor/three.module.js';
import {HDRLoader} from './vendor/HDRLoader.js';
import {createMedalAssets} from './medal-assets.mjs';
import {modelKey,instanceKey,validDisplay} from './model-registry.mjs';
import {BadgeFaceFeedback} from './feedback.mjs';
import {CabinetFlight,renderScale} from './cabinet-flight.mjs';
import {BadgeDampedTrackball,cubicTiming,galleryFocus,galleryInvitation,galleryDragIntent} from './gallery-motion.mjs';

const canvas=document.getElementById('view'), status=document.getElementById('status');
const scroll=document.getElementById('collection'), grid=document.getElementById('grid');
const detail=document.getElementById('detail'), title=document.getElementById('detail-title'), serial=document.getElementById('detail-serial');
let awardFlow=new BadgeAwardFlow();
const owner=document.getElementById('detail-owner');
const designNote=document.getElementById('design-note'),designText=document.getElementById('design-note-text'),storyToggle=document.getElementById('story-toggle');
let noteSignature='',playProgress=0,playTarget=0;
function setPlaying(value){const target=value?1:0;if(target===playTarget)return;playTarget=target;host('playing',value);requestDraw();}
function stageBounds(){
 const n=config.insets||{},value=(key,max)=>Math.max(0,Math.min(max,Number(n[key])||0));
 const left=value('left',innerWidth*.45),right=value('right',innerWidth*.8),top=value('top',innerHeight*.8),bottom=value('bottom',innerHeight*.6);
 return {left,right,top,bottom,width:Math.max(1,innerWidth-left-right),height:Math.max(1,innerHeight-top-bottom),safeTop:value('safeTop',innerHeight*.2),safeBottom:value('safeBottom',innerHeight*.2)};
}
const control=new BadgeDampedTrackball(3.2), flight=new CabinetFlight(900), face=new BadgeFaceFeedback();
const items=new Map(), tiles=new Map(), loading=new Map(), failures=new Set();
// Tiny geometry metadata may outlive a GPU instance. Releasing an offscreen
// crown must not shrink its row and move every following medal under the finger.
const tileMetrics=new Map();
const preparation=new BadgePreparationWatchdog(()=>{if(!disposed){updateTiles();syncSelection();requestDraw();}});
const previewControls=new Map();
const identity=new THREE.Quaternion(), returnQ=new THREE.Quaternion(), entryQ=new THREE.Quaternion(), spin=new THREE.Quaternion();
let overviewGesture=null,suppressClick=null,pendingSelection=null;
let scrollMoving=false,scrollSettleTimer=0;
let frameCache=null,lastOverviewActive=null;
const gestureCounts={starts:0,moves:0,rotations:0,cancels:0};
const motionFrameTimes=[];
const invitations=new Map(),invitationPlayed=new Set();
let config={slots:[],inspection:null,dark:false,reducedMotion:false,...globalThis.__BADGE_CONFIG__};
let renderer,scene,camera,assets,environment,selected,selectedValue,origin,active=true,disposed=false,frame=0,last=0;
let frameRequestedAt=0;
const scenePreparation=new AbortController();
let ready=false,notified=false,pixelRatio=0,userPoseRevision=0,firstFrameMillis=null,structure='',entryPending=false,returnStart=1;
let layoutWidth=0,layoutHeight=0;
const collectionLayout=new GalleryLayoutCache();
let scrollSyncPending=false,invitationWait=0;
let presented=!config.deferInvitation,presentationAcknowledged=false,invitationAfter=0,invitationCompleted=0;
const started=performance.now();
const host=(method,value)=>{if(!disposed)try{globalThis.BadgeHost?.[method]?.(value);}catch{}};
const feedback=value=>{if(active&&!config.reducedMotion)host('haptic',value);};
const values=()=>Array.isArray(config.slots)?config.slots:[];
function requestDraw(){if(renderer&&assets&&active&&!disposed&&!frame){frameRequestedAt=performance.now();frame=requestAnimationFrame(draw);}}
function viewport(){return {width:Math.max(1,innerWidth),height:Math.max(1,innerHeight)};}
function quality(moving){
  const {width,height}=viewport(), ratio=renderScale(width,height,devicePixelRatio,moving,renderer.capabilities.maxTextureSize,flight.phase==='inspect');
  // Three floors the physical size. Rounding here repeatedly resized canvases
  // on fractional Android densities (for example 393 CSS px at density 2.75).
  if(pixelRatio!==ratio||canvas.width!==Math.floor(width*ratio)||canvas.height!==Math.floor(height*ratio)){
    pixelRatio=ratio;renderer.setDrawingBufferSize(width,height,ratio);
  }
  if(camera.left!==-width/2||camera.right!==width/2||camera.top!==height/2||camera.bottom!==-height/2){
    camera.left=-width/2;camera.right=width/2;camera.top=height/2;camera.bottom=-height/2;camera.updateProjectionMatrix();
  }
  control.width=width;control.height=height;
}
function galleryLayout(){
  return collectionLayout.sample(scroll.scrollTop,innerHeight,()=>{
    const measured=[];
    for(const value of values()){
      const id=instanceKey(value),rect=tiles.get(id)?.querySelector('.medal')?.getBoundingClientRect();
      if(rect)measured.push({id,value,rect});
    }
    return measured;
  });
}
function tileRect(value){return galleryLayout().rects.get(instanceKey(value))??null;}
function visibleValues(){
  const visible=galleryLayout().near.slice(0,24);
  // Keep the visible collection warm while inspecting; returning must not decode it again.
  if(selectedValue&&!visible.some(v=>instanceKey(v)===instanceKey(selectedValue)))visible.push(selectedValue);
  return visible;
}
function place(item,point){item.object.position.set(point.x-innerWidth/2,innerHeight/2-point.y,0);item.object.scale.setScalar(point.scale);}
function previewControl(id){if(!previewControls.has(id))previewControls.set(id,new BadgeDampedTrackball(3.2));return previewControls.get(id);}
function stopOverview(interacted=false,grabbedId=null){
  if(interacted)for(const value of values()){
    const id=instanceKey(value),r=tileRect(value);
    if(invitations.has(id)||items.has(id)&&r&&r.bottom>0&&r.top<innerHeight)invitationPlayed.add(id);
  }
  // Take over the touched model at its visible angle. Other invitations return
  // to their own resting pose; touching a neighbour must not leave a ring skewed.
  const grabbed=items.get(grabbedId),grabbedControl=previewControls.get(grabbedId);
  if(interacted&&grabbed&&grabbedControl)grabbedControl.setPose({q:grabbed.object.quaternion.toArray(),zoom:grabbedControl.zoom});
  invitations.clear();
  invitationWait=0;
  invitationAfter=performance.now()+180;
  for(const c of previewControls.values())c.stop();
  if(overviewGesture){
    const g=overviewGesture;overviewGesture=null;g.medal.classList.remove('dragging');
    const tile=g.medal.parentElement;
    if(tile.hasPointerCapture(g.pointerId))tile.releasePointerCapture(g.pointerId);
  }
}
function overviewDown(e,value,medal){
  if(!active||disposed||flight.phase!=='overview'||!items.has(instanceKey(value)))return;
  // A second finger cancels this gesture; pinch zoom is available in the enlarged view.
  if(overviewGesture){suppressClick=overviewGesture.id;stopOverview(true,overviewGesture.id);return;}
  const id=instanceKey(value);invitationPlayed.add(id);stopOverview(true,id);suppressClick=null;
  const c=previewControl(id),rect=medal.getBoundingClientRect();
  lastOverviewActive=id;
  motionFrameTimes.length=0;
  gestureCounts.starts++;
  c.resize(rect.width,rect.height);c.down(e.pointerId,e.clientX-rect.left,e.clientY-rect.top,e.timeStamp);
  overviewGesture={id,c,medal,rect,pointerId:e.pointerId,x:e.clientX,y:e.clientY,dragged:false,scrolling:false,scrollTop:scroll.scrollTop};
  medal.parentElement.setPointerCapture(e.pointerId);
}
function overviewMove(e){
  const g=overviewGesture;if(!g||g.pointerId!==e.pointerId||!active||flight.phase!=='overview')return;
  gestureCounts.moves++;
  if(!g.dragged&&!g.scrolling){
    const intent=galleryDragIntent(e.clientX-g.x,e.clientY-g.y,e.pointerType);
    if(intent==='pending')return;
    if(intent==='scroll'){suppressClick=g.id;g.scrolling=true;g.c.stop();}
    else {g.dragged=true;g.medal.classList.add('dragging');feedback('touch');last=0;}
  }
  e.preventDefault();
  if(g.scrolling){scroll.scrollTop=g.scrollTop-(e.clientY-g.y);return;}
  if(g.c.move(e.pointerId,e.clientX-g.rect.left,e.clientY-g.rect.top,e.timeStamp)){gestureCounts.rotations++;userPoseRevision++;requestDraw();}
}
function overviewUp(e){
  const g=overviewGesture;if(!g||g.pointerId!==e.pointerId)return;
  if(g.dragged||g.scrolling||e.type!=='pointerup')suppressClick=g.id;
  if(e.type==='pointercancel')gestureCounts.cancels++;
  if(e.type==='pointerup'&&g.dragged&&!config.reducedMotion)g.c.up(e.pointerId,e.timeStamp);else g.c.stop();
  g.medal.classList.remove('dragging');overviewGesture=null;requestDraw();
}
function updateTiles(){
  collectionLayout.invalidate();
  // Embedded Android WebViews can report transient/zero dynamic viewport CSS units.
  // Resolve collection columns from the same measured viewport as the renderer.
  const bounds=stageBounds(),w=bounds.width,h=bounds.height,css=document.documentElement.style;
  for(const key of ['top','right','bottom','left'])css.setProperty('--stage-'+key,bounds[key]+'px');
  layoutWidth=innerWidth;layoutHeight=innerHeight;document.body.dataset.compact=String(h<=360);
  css.setProperty('--gallery-max-width',w>=700?'1000px':'600px');
  document.body.dataset.theme=config.dark?'classic':config.night?'night':'clearsky';
  document.body.dataset.reduced=String(config.reducedMotion);document.body.dataset.active=String(active);
  document.body.dataset.count=String(Math.min(values().length,4));
  document.documentElement.style.setProperty('--font-scale',String(Math.max(1,Math.min(2,config.fontScale||1))));
  css.setProperty('--columns',String(w>=700?2:1));
  const next=JSON.stringify(values().map(v=>[instanceKey(v),v.slot,v.name,v.engravingPublicId]));
  if(next!==structure){
    stopOverview();
    structure=next;grid.replaceChildren();tiles.clear();
    const retained=new Set(values().map(instanceKey));for(const id of previewControls.keys())if(!retained.has(id))previewControls.delete(id);
    for(const value of values()){
      const button=document.createElement('button');button.type='button';button.className='tile';
      button.setAttribute('aria-label',`${value.name||'徽章'}，编号 ${String(value.serial).padStart(6,'0')}，左右拖动旋转，轻触放大`);
      const medal=document.createElement('span');medal.className='medal';medal.setAttribute('aria-hidden','true');
      const name=document.createElement('span');name.className='name';name.textContent=value.name||'徽章';
      const number=document.createElement('span');number.className='serial';number.textContent=String(value.serial).padStart(6,'0');
      const caption=document.createElement('span');caption.className='caption';
      const hint=document.createElement('span');hint.className='hint';hint.textContent='拖动旋转 · 轻触放大';hint.setAttribute('aria-hidden','true');
      caption.append(name,number);if(value.engravingPublicId){const by=document.createElement('span');by.className='owner';by.textContent='拥有者：'+value.engravingPublicId;caption.append(by);}caption.append(hint);button.append(medal,caption);grid.append(button);tiles.set(instanceKey(value),button);
      // A fresh tap on the caption is independent of a preceding medal drag.
      button.addEventListener('pointerdown',()=>{suppressClick=null;},{capture:true});
      // Some Android WebViews retarget a decorative button child to its button.
      // Handle the model's visible bounds on the tile; caption taps stay independent.
      button.addEventListener('pointerdown',e=>{
        const r=medal.getBoundingClientRect();
        if(e.clientX>=r.left&&e.clientX<=r.right&&e.clientY>=r.top&&e.clientY<=r.bottom)overviewDown(e,value,medal);
      });
      button.addEventListener('pointermove',overviewMove);
      for(const event of ['pointerup','pointercancel','lostpointercapture'])button.addEventListener(event,overviewUp);
      button.addEventListener('keydown',e=>{
        if(!active||flight.phase!=='overview'||!items.has(instanceKey(value))||!['ArrowLeft','ArrowRight'].includes(e.key))return;
        e.preventDefault();invitationPlayed.add(instanceKey(value));stopOverview(true,instanceKey(value));previewControl(instanceKey(value)).rotate([0,1,0],e.key==='ArrowLeft'?-.25:.25);userPoseRevision++;requestDraw();
      });
      button.addEventListener('click',e=>{if(!active||flight.phase!=='overview')return;
        if(suppressClick===instanceKey(value)&&e.detail!==0){suppressClick=null;e.preventDefault();return;}
        const latest=values().find(v=>instanceKey(v)===instanceKey(value))||value;
        const retryNative=latest.badgeId!=='first-light-016'&&latest.modelStatus==='ready'&&
          (failures.has(modelKey(latest))||!validDisplay(latest));
        failures.delete(modelKey(latest));preparation.retry(instanceKey(latest));feedback('touch');pendingSelection=instanceKey(latest);
        if((retryNative||!validDisplay(latest))&&preparation.request(pendingSelection))host('prepareSlot',latest.slot);
        syncItems();
      });
    }
  }
  preparation.retain(new Set(values().map(instanceKey)));
  const owned=new Set(values().map(instanceKey));
  for(const id of tileMetrics.keys())if(!owned.has(id))tileMetrics.delete(id);
  // Batch reads before height writes. A tall newly loaded medal must not force
  // a separate synchronous grid layout for every following collection row.
  const heights=[];
  for(const value of values()){
    const id=instanceKey(value),item=items.get(id),assetKey=item?.assetKey??modelKey(value),medal=tiles.get(id)?.querySelector('.medal');
    if(item)tileMetrics.set(id,{assetKey,ratio:item.heightRatio});
    if(tileMetrics.get(id)?.assetKey!==assetKey)tileMetrics.delete(id);
    if(medal)heights.push([medal,medal.getBoundingClientRect().width*Math.max(1,tileMetrics.get(id)?.ratio||1)+'px']);
  }
  for(const [medal,height]of heights)if(medal.style.height!==height)medal.style.height=height;
  for(const value of values()){
    preparation.observe(instanceKey(value),value.modelStatus,value.modelAttempt??0);
    const tile=tiles.get(instanceKey(value)), has=items.has(instanceKey(value));
    tile?.classList.toggle('pending',!has);
    if(tile){const failed=value.modelStatus==='error'||failures.has(modelKey(value))||preparation.failed.has(instanceKey(value))||value.modelStatus==='ready'&&!validDisplay(value)&&!preparation.pending.has(instanceKey(value));tile.classList.toggle('failed',!has&&failed);tile.setAttribute('aria-busy',String(!has&&!failed));
      tile.dataset.note=has?'':failed?'未能加载 · 轻触重试':pendingSelection===instanceKey(value)?'准备好后展开':
        value.modelStatus==='ready'?'正在打开三维模型':value.modelStatus==='loading'?'正在下载徽章模型':'正在准备徽章';
      const hint=tile.querySelector('.hint'),text=has?'拖动旋转 · 轻触放大':tile.dataset.note;
      if(hint.textContent!==text)hint.textContent=text;
    }
  }
  collectionLayout.invalidate();
}
function syncItems(scrollOnly=false){
  if(!assets||disposed)return;
  let changed=false;
  const visible=visibleValues(), keep=new Set(visible.map(instanceKey)), owned=new Set(values().map(instanceKey));
  // Keep a small working set across viewport changes; authority still comes from this visit's wallet.
  for(const id of items.keys())if(keep.size<Math.max(9,visible.length)&&owned.has(id))keep.add(id);
  if(selectedValue&&!owned.has(instanceKey(selectedValue)))clearSelection();
  if(pendingSelection&&!keep.has(pendingSelection))pendingSelection=null;
  // Visible medals and up to nine recent instances stay warm; the rest are released.
  for(const [id,item]of items)if(!keep.has(id)&&item!==selected){previewControls.get(id)?.stop();assets.releaseInstance(item);items.delete(id);changed=true;}
  for(const value of visible){
    const id=instanceKey(value), key=modelKey(value);
    if(preparation.pending.has(id)||preparation.failed.has(id))continue;
    if(!validDisplay(value)){
      if(active&&value.modelStatus!=='error'&&value.modelStatus!=='ready'&&preparation.request(id)){
        host('prepareSlot',value.slot);
      }
      continue;
    }
    if(failures.has(key))continue;
    const existing=items.get(id);
    if(existing&&existing.assetKey!==key){
      if(assets.has(key)){
        let replacement;
        try{replacement=assets.instance(value,{slots:{[value.slot]:{badgeBottomCenter:[0,0,0],targetVisualWidth:1}}},config.dark);}
        catch{failures.add(key);changed=true;continue;}
        replacement.object.position.copy(existing.object.position);replacement.object.quaternion.copy(existing.object.quaternion);replacement.object.scale.copy(existing.object.scale);
        scene.add(replacement.object);if(selected===existing)selected=replacement;
        assets.releaseInstance(existing);items.set(id,replacement);changed=true;
      }else{
        if(!loading.has(key)){
          const pending=assets.ensure(value).catch(()=>{if(!disposed)failures.add(key);}).finally(()=>{
            loading.delete(key);if(!disposed){syncItems();syncSelection();updateTiles();requestDraw();}
          });loading.set(key,pending);
        }
        continue;
      }
    }
    if(items.has(id)){assets.updateInstance(items.get(id),value);continue;}
    if(assets.has(key)){
      const layout={slots:{[value.slot]:{badgeBottomCenter:[0,0,0],targetVisualWidth:1}}};
      try{const item=assets.instance(value,layout,config.dark);scene.add(item.object);items.set(id,item);changed=true;}
      catch{failures.add(key);changed=true;}
    }else if(!loading.has(key)){
      const pending=assets.ensure(value).catch(()=>{if(!disposed)failures.add(key);}).finally(()=>{
        loading.delete(key);if(!disposed){syncItems();syncSelection();updateTiles();requestDraw();}
      });loading.set(key,pending);
    }
  }
  // Pending loads retain their prototype until they settle, including after a fast scroll.
  assets.trim([...items.values()].map(i=>i.assetKey).concat([...loading.keys()]));
  if(!scrollOnly||changed){updateTiles();syncSelection();}
  // A newly measured aspect ratio can bring another row into view. Recheck on
  // the next frame even when the total grid height happens to stay unchanged.
  if(changed)scrollSyncPending=true;
  if(!scrollOnly||changed)requestDraw();
  if(pendingSelection&&items.has(pendingSelection)&&active&&flight.phase==='overview'){
    const value=values().find(v=>instanceKey(v)===pendingSelection);pendingSelection=null;if(value)host('selectSlot',value.slot);
  }
}
function syncSelection(){
  const requested=config.inspection;
  if(requested){
    const id=instanceKey(requested);
    if(selectedValue&&instanceKey(selectedValue)!==id){clearSelection();}
    selectedValue=requested;
    owner.textContent=requested.engravingPublicId?'拥有者：'+requested.engravingPublicId:'';owner.hidden=!requested.engravingPublicId;
    title.textContent=requested.name||'徽章';serial.textContent=String(requested.serial).padStart(6,'0');
    if(!selected){
      selected=items.get(id);
      if(selected){entryQ.copy(selected.object.quaternion);stopOverview(true);previewControl(id).setPose({q:entryQ.toArray(),zoom:1});
        origin=tileRect(requested)||{x:innerWidth/2,y:innerHeight*.2,scale:100};if(!selected.bodySized)origin.scale/=Math.max(1,selected.heightRatio);control.reset();face.reset();flight.enter();entryPending=false;last=0;}
      else entryPending=true;
    }else if(flight.target===0){flight.enter();}
  }else if(selectedValue){
    entryPending=false;
    if(selected){if(flight.target!==0){
      returnStart=Math.max(.00001,cubicTiming(flight.progress));returnQ.copy(selected.object.quaternion);
      control.stop();flight.back();last=0;
    }}
    else clearSelection();
  }
  const inspecting=!!selectedValue;
  scroll.inert=inspecting;scroll.style.overflowY=inspecting?'hidden':'auto';
  // Some Android WebViews retain the inert collection's compositor hit region.
  // Explicitly release that overlay so real touch reaches the inspection canvas.
  scroll.style.pointerEvents=inspecting?'none':'auto';
  canvas.style.pointerEvents=inspecting?'auto':'none';
  status.hidden=!entryPending||config.inspection?.modelStatus==='error';
  if(entryPending)status.textContent=failures.has(modelKey(config.inspection))||preparation.failed.has(instanceKey(config.inspection))||config.inspection?.modelStatus==='ready'&&!validDisplay(config.inspection)?'徽章模型暂未加载，请返回重试':'正在准备徽章';
}
function syncDesignNote(){
  const note=typeof selectedValue?.designNote==='string'&&selectedValue.designNote.length<=160?selectedValue.designNote.trim():'';
  const full=stageBounds().height>=420*Math.max(1,Math.min(2,config.fontScale||1));
  const signature=note+'|'+full+'|'+!!selected;
  if(signature===noteSignature)return;noteSignature=signature;
  designText.textContent=note;designNote.hidden=!note||!full;storyToggle.hidden=!selected||!note||full;
}
storyToggle.addEventListener('click',e=>{e.preventDefault();e.stopPropagation();if(active&&flight.phase==='inspect'&&selectedValue)host('storySlot',selectedValue.slot);});
function clearSelection(){setPlaying(false);playProgress=0;selected=null;selectedValue=null;origin=null;control.reset();face.reset();flight.progress=0;flight.target=0;detail.hidden=true;}
function draw(now){
  frame=0;if(!active||disposed||!renderer||!assets)return;
  if(layoutWidth!==innerWidth||layoutHeight!==innerHeight){updateTiles();syncItems();}
  if(scrollSyncPending){scrollSyncPending=false;syncItems(true);}
  const dt=last?Math.min(250,Math.max(0,now-last)):0;last=now;
  const previous=flight.phase;flight.tick(dt,config.reducedMotion);
  let invitationWaiting=false;
  if(active&&presented&&flight.phase==='overview'&&!overviewGesture){
    const visible=galleryLayout().near.filter(value=>{const r=tileRect(value);return r.bottom>0&&r.top<innerHeight;});
    const waiting=visible.some(value=>!items.has(instanceKey(value))&&value.modelStatus!=='error'&&!failures.has(modelKey(value)));
    // Give concurrently decoding visible models a short chance to join the same
    // wave. A missing/slow model must never hold the others indefinitely.
    if(waiting&&visible.some(value=>items.has(instanceKey(value))))invitationWait+=dt;
    const elapsed=invitations.values().next().value?.elapsed??-dt;
    for(const value of visible){
      const id=instanceKey(value),r=tileRect(value);
      if(invitationPlayed.has(id)||invitations.has(id)||!items.has(id)||!r||r.bottom<=0||r.top>=innerHeight)continue;
      if(config.reducedMotion){invitationPlayed.add(id);continue;}
      if(now<invitationAfter){invitationWaiting=true;continue;}
      if(!invitations.size&&waiting&&invitationWait<400){invitationWaiting=true;continue;}
      // Every ready medal shares one phase, with no pair limit or row stagger.
      // A late model joins the running wave instead of starting a second queue.
      invitations.set(id,{elapsed});
    }
  }
  let previewGliding=false;
  if(flight.phase==='overview')for(const [id,c]of previewControls)if(items.has(id))previewGliding=c.tick(dt/1000)||previewGliding;
  for(const [id,cue]of invitations){cue.elapsed+=dt;if(cue.elapsed>=1300){invitationPlayed.add(id);invitationCompleted++;invitations.delete(id);}else if(config.reducedMotion||!items.has(id))invitations.delete(id);}
  playProgress=config.reducedMotion?playTarget:playProgress+Math.max(-dt/520,Math.min(dt/520,playTarget-playProgress));
  const play=cubicTiming(playProgress),playMoving=playProgress!==playTarget;
  const gliding=flight.phase==='inspect'&&control.tick(dt/1000), moving=scrollMoving||playMoving||flight.moving||gliding||previewGliding||invitations.size>0||control.pointers.size>0||!!overviewGesture?.dragged;
  if(moving&&dt>0){motionFrameTimes.push(dt);if(motionFrameTimes.length>120)motionFrameTimes.shift();}
  quality(moving);renderer.transmissionResolutionScale=.5;
  const t=cubicTiming(flight.progress);
  detail.hidden=!selected;syncDesignNote();
  const bounds=stageBounds(),identityHeight=title.offsetHeight+serial.offsetHeight+owner.offsetHeight;
  const labelHeight=Math.max(49*Math.max(1,Math.min(2,config.fontScale||1)),detail.offsetHeight);
  const intro=galleryFocus(bounds.width,bounds.height,selected?.heightRatio,selected?.radius,selected?.bodySized,labelHeight);
  intro.x+=bounds.left;intro.y+=bounds.top;intro.labelTop+=bounds.top;
  const dockTop=Math.max(bounds.safeTop,innerHeight-bounds.bottom-identityHeight-24);
  const playCenter=(bounds.safeTop+Math.max(bounds.safeTop,dockTop-20))/2;
  const focus={...intro,y:THREE.MathUtils.lerp(intro.y,playCenter,play)};
  scroll.style.opacity=String(1-Math.min(1,t*3));
  detail.hidden=!selected;detail.style.opacity=String(Math.max(0,(t-.65)/.35));
  designNote.style.opacity=String(1-play);designNote.setAttribute('aria-hidden',String(playTarget===1));
  storyToggle.style.opacity=String(Number(detail.style.opacity)*(1-play));storyToggle.style.pointerEvents=playTarget?'none':'auto';storyToggle.tabIndex=playTarget?-1:0;
  const introTop=Math.max(bounds.top,Math.min(innerHeight-bounds.bottom-detail.offsetHeight-12,intro.labelTop));
  detail.style.top=THREE.MathUtils.lerp(introTop,dockTop,play)+'px';
  // Inspection has no continuous ambient animation: when the medal settles rendering sleeps.
  let settling=false;
  for(const [id,item]of items){
    if(item===selected){
      const a=origin||focus;
      place(item,{x:THREE.MathUtils.lerp(a.x,focus.x,t),y:THREE.MathUtils.lerp(a.y,focus.y,t),scale:THREE.MathUtils.lerp(a.scale,focus.scale*control.zoom,t)});
      item.object.visible=true;
      if(flight.phase==='inspect'){
        spin.fromArray(control.q);const blend=config.reducedMotion?1:1-Math.exp(-dt/38);
        item.object.quaternion.slerp(spin,blend);
        settling=item.object.quaternion.angleTo(spin)>.0002;
        if(!settling)item.object.quaternion.copy(spin);
      }else if(flight.target===0)item.object.quaternion.copy(entryQ).slerp(returnQ,Math.min(1,t/returnStart));
      else {spin.setFromAxisAngle(new THREE.Vector3(0,1,0),-Math.PI*2*(1-cubicTiming(flight.progress,.33,0,.2,1)));
        item.object.quaternion.copy(entryQ).slerp(identity,t).premultiply(spin);}
    }else{
      const r=galleryLayout().rects.get(id);
      item.object.visible=!!r&&r.bottom>0&&r.top<innerHeight&&t<.12;
      item.gallerySettling=false;
      if(item.object.visible){place(item,{...r,scale:r.scale/(item.bodySized?1:Math.max(1,item.heightRatio))});
        spin.fromArray(previewControl(id).q);
        if(invitations.has(id))spin.setFromAxisAngle(new THREE.Vector3(0,1,0),galleryInvitation(Math.max(0,invitations.get(id).elapsed)/1300));
        item.object.quaternion.slerp(spin,config.reducedMotion?1:1-Math.exp(-dt/38));
        item.gallerySettling=item.object.quaternion.angleTo(spin)>.0002;
        if(item.gallerySettling)settling=true;else item.object.quaternion.copy(spin);
      }
    }
  }
  // A close-up ring is only a few hundred scene units deep. The old 10,000-unit
  // orthographic range wastes depth precision on its closely fitted gold parts.
  const ringDepth=selectedValue?.badgeId==='clear-sky-ring'?Math.max(25,...[...items.values()].filter(i=>i.object.visible).map(i=>i.radius*i.object.scale.x))+12:null;
  const near=ringDepth?Math.max(.1,camera.position.z-ringDepth):.1,far=ringDepth?camera.position.z+ringDepth:10000;
  if(camera.near!==near||camera.far!==far){camera.near=near;camera.far=far;camera.updateProjectionMatrix();}
  try{
    renderer.setScissorTest(false);renderer.clear();
    if(flight.phase==='overview'){renderer.setScissor(bounds.left,bounds.bottom,bounds.width,bounds.height);renderer.setScissorTest(true);}
    const liveIds=new Set([...invitations.keys(),...[...items].filter(([,item])=>item.gallerySettling).map(([id])=>id),...(!invitations.size?[lastOverviewActive]:[])]);
    const live=[...liveIds].map(id=>items.get(id)).filter(item=>item?.object.visible);
    if(flight.phase==='overview'&&!scrollMoving&&live.length)frameCache.render(live);
    else renderer.render(scene,camera);
  }catch{fail();return;}
  if(!notified){notified=true;host('status','ready');}
  if(!ready&&items.size>0){ready=true;firstFrameMillis=Math.round(now-started);}
  const awardValue=Number.isInteger(config.awardSlot)?values().find(v=>v.slot===config.awardSlot):null;
  const awardId=awardValue?instanceKey(awardValue):null;
  const awardAction=awardValue?awardFlow.step({active,presented,ready:items.has(awardId),
    cued:invitationPlayed.has(awardId),phase:selectedValue&&instanceKey(selectedValue)!==awardId?'other':flight.phase,
    dt,acknowledged:config.awardAcknowledged===true}):null;
  if(awardAction==='enter')host('selectSlot',awardValue.slot);
  if(awardAction==='presented')host('awardPresented',awardValue.slot);
  if(previous!==flight.phase&&flight.phase==='inspect')feedback('entry');
  if(previous!=='overview'&&flight.phase==='overview'){clearSelection();syncSelection();syncItems();}
  if(flight.phase==='inspect'){const event=face.sample(control.q,now);if(event)feedback(event);}
  if((awardValue&&!config.awardAcknowledged&&awardFlow.waiting(flight.phase))||playMoving||gliding||previewGliding||invitations.size||invitationWaiting||settling||flight.moving||face.pending())requestDraw();
  else if(!moving&&pixelRatio!==renderScale(innerWidth,innerHeight,devicePixelRatio,false,renderer.capabilities.maxTextureSize)){
    quality(false);renderer.render(scene,camera);
  }
}
function pause(){awardFlow.pause();preparation.clear();active=false;document.body.dataset.active='false';flight.pause();control.stop();stopOverview();frameCache?.clear();lastOverviewActive=null;clearTimeout(scrollSettleTimer);scrollMoving=false;scrollSyncPending=false;if(frame)cancelAnimationFrame(frame);frame=0;last=0;}
function resume(){if(disposed)return;active=true;document.body.dataset.active='true';flight.resume();last=0;syncItems();requestDraw();}
function resize(){if(disposed)return;control.stop();stopOverview();updateTiles();if(selectedValue&&origin){origin=tileRect(selectedValue)||origin;if(selected&&!selected.bodySized)origin.scale/=Math.max(1,selected.heightRatio);}syncItems();requestDraw();}
function visibility(){document.hidden?pause():resume();}
function dispose(){
  if(disposed)return;pause();disposed=true;flight.dispose();
  scenePreparation.abort();
  removeEventListener('resize',resize);removeEventListener('pagehide',dispose);document.removeEventListener('visibilitychange',visibility);
  removeEventListener('error',scriptFailure);removeEventListener('unhandledrejection',promiseFailure);
  scroll.removeEventListener('scroll',onScroll);for(const [name,fn]of listeners)canvas.removeEventListener(name,fn);
  collectionResize?.disconnect();document.fonts?.removeEventListener('loadingdone',collectionChanged);
  frameCache?.dispose();for(const item of items.values())assets?.releaseInstance(item);items.clear();tileMetrics.clear();previewControls.clear();assets?.dispose();environment?.dispose();renderer?.dispose();renderer?.forceContextLoss();
}
function fail(){if(disposed)return;status.hidden=false;status.textContent='三维视图暂时无法加载';host('status','render_failed');dispose();}
function down(e){if(!active||disposed||flight.phase!=='inspect')return;gestureCounts.starts++;e.preventDefault();motionFrameTimes.length=0;control.down(e.pointerId,e.offsetX,e.offsetY,e.timeStamp);canvas.setPointerCapture(e.pointerId);feedback('touch');last=0;requestDraw();}
function move(e){if(!active||flight.phase!=='inspect')return;gestureCounts.moves++;if(control.move(e.pointerId,e.offsetX,e.offsetY,e.timeStamp)){gestureCounts.rotations++;e.preventDefault();userPoseRevision++;setPlaying(true);requestDraw();}}
function up(e){if(e.type==='pointerup'){control.up(e.pointerId,e.timeStamp);if(config.reducedMotion)control.stop();}else if(control.pointers.has(e.pointerId))control.stop();requestDraw();}
function wheel(e){if(!active||flight.phase!=='inspect')return;e.preventDefault();setPlaying(true);control.zoomBy(Math.exp(-e.deltaY*.001));requestDraw();}
function keydown(e){const actions={ArrowLeft:()=>api.rotate([0,1,0],-.25),ArrowRight:()=>api.rotate([0,1,0],.25),ArrowUp:()=>api.rotate([1,0,0],-.2),ArrowDown:()=>api.rotate([1,0,0],.2),'+':()=>api.zoom(1.2),'-':()=>api.zoom(1/1.2),'0':()=>api.reset()};if(actions[e.key]){e.preventDefault();actions[e.key]();}}
const listeners=[['pointerdown',down],['pointermove',move],['pointerup',up],['pointercancel',up],['lostpointercapture',up],['wheel',wheel],['keydown',keydown],['webglcontextlost',e=>{e.preventDefault();fail();}]];
const api=globalThis.BadgeView={
  configure(next){if(disposed)return;if(next.awardSlot!==config.awardSlot){awardFlow=new BadgeAwardFlow();const value=next.slots?.find(v=>v.slot===next.awardSlot);if(value)invitationPlayed.delete(instanceKey(value));}const wasReduced=config.reducedMotion;config={...config,...next};if(config.deferInvitation&&!presentationAcknowledged)presented=false;if(config.reducedMotion&&!wasReduced){control.stop();stopOverview();}updateTiles();syncSelection();syncItems();requestDraw();},
  beginVisit(next){
    if(disposed)return;
    // A fresh verified wallet config replaces the old host, while decoded meshes stay reusable.
    stopOverview();clearSelection();frameCache?.clear();lastOverviewActive=null;flight.pause();flight.resume();
    // A new cabinet visit starts rings at the approved front orientation.
    // Enlargement/return within this visit still preserves the user's pose.
    for(const [id,item]of items)if(item.badgeId==='clear-sky-ring'){
      previewControls.get(id)?.reset();item.object.quaternion.identity();item.gallerySettling=false;
    }
    awardFlow=new BadgeAwardFlow();pendingSelection=null;entryPending=false;preparation.clear();failures.clear();
    invitations.clear();invitationPlayed.clear();invitationCompleted=0;invitationWait=0;
    presentationAcknowledged=false;presented=false;notified=false;
    config={...next,inspection:next.inspection??null};scroll.scrollTop=0;
    updateTiles();syncSelection();syncItems();requestDraw();
  },
  pause,resume,dispose,
  healthy(){return !disposed&&active&&!(frame&&performance.now()-frameRequestedAt>10_000)&&!renderer?.getContext().isContextLost();},
  presented(){if(disposed||presentationAcknowledged)return;presentationAcknowledged=true;presented=true;invitationAfter=performance.now()+120;last=0;requestDraw();},
  reset(){if(flight.phase!=='inspect')return;setPlaying(false);control.reset();face.reset();requestDraw();feedback('reset');},
  rotate(axis,angle){if(flight.phase!=='inspect')return;setPlaying(true);control.rotate(axis,angle);requestDraw();},
  zoom(factor){if(flight.phase!=='inspect')return;setPlaying(true);control.zoomBy(factor);requestDraw();},
  diagnostics(){return {modelLoaded:items.size>0,labelReady:items.size>0,active,disposed,pendingFrame:!!frame,firstFrame:ready,firstFrameMillis,
    motionFrameMedianMillis:[...motionFrameTimes].sort((a,b)=>a-b)[Math.floor(motionFrameTimes.length/2)]??null,motionFrameCount:motionFrameTimes.length,gestureCounts:{...gestureCounts},scrollMoving,frameCacheHits:frameCache?.hits??0,frameCacheCaptures:frameCache?.captures??0,playing:playTarget===1,playProgress,stageBounds:stageBounds(),canvasFullViewport:canvas.clientHeight===innerHeight,scissorEnabled:renderer?.getScissorTest(),awardPresented:awardFlow.sent,awardSlot:config.awardSlot,sceneStyle:config.dark?'classic':'clearsky',phase:flight.phase,transitionProgress:flight.progress,realMedalCount:items.size,
    slotSerials:[...items.values()].map(v=>({slot:v.slot,serial:v.serial,badgeId:v.badgeId})),pixelRatio,userPoseRevision,activePointers:control.pointers.size,pose:control.pose(),
    galleryPoses:[...items].map(([id,item])=>({id,pose:previewControl(id).pose(),renderedQ:item.object.quaternion.toArray()})),galleryDragging:!!overviewGesture?.dragged,invitationActive:invitations.size>0,
    invitationIds:[...invitations.keys()],invitationElapsed:[...invitations.values()].map(c=>c.elapsed),invitationCompleted,presented,pendingSelection:pendingSelection!==null,medalMetrics:[...items.values()].map(i=>({slot:i.slot,bodySized:i.bodySized,heightRatio:i.heightRatio,radius:i.radius})),selectedAssetKey:selected?.assetKey??null,selectedPresentation:selected?{id:instanceKey(selectedValue),x:selected.object.position.x+innerWidth/2,y:innerHeight/2-selected.object.position.y,scale:selected.object.scale.x,q:selected.object.quaternion.toArray()}:null,
    firstMedalWidth:values()[0]?tiles.get(instanceKey(values()[0]))?.querySelector('.medal')?.getBoundingClientRect().width:0,
    firstMedalHeight:values()[0]?tiles.get(instanceKey(values()[0]))?.querySelector('.medal')?.getBoundingClientRect().height:0,
    ringGemOptics:selected?.label.userData.gemOptics??null,
    drawCalls:renderer?.info.render.calls,triangles:renderer?.info.render.triangles,geometryCount:renderer?.info.memory.geometries,textureCount:renderer?.info.memory.textures,contextLost:renderer?.getContext().isContextLost()??false};}
};
async function boot(){try{
  renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'high-performance'});
  renderer.setClearColor(0,0);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.95;
  scene=new THREE.Scene();camera=new THREE.OrthographicCamera(-1,1,1,-1,.1,10000);camera.position.z=2000;
  scene.add(new THREE.HemisphereLight(0xe4fff5,0x263936,.28));
  for(const [color,power,position]of [[0xffedcf,1.6,[-3,8,6]],[0xe1f4ff,.5,[4,4,4]],[0xd6fff0,1.15,[1,5,-3]]]){const light=new THREE.DirectionalLight(color,power);light.position.set(...position);scene.add(light);}
  const [manifest,hdr]=await loadBadgeSceneResources({
    loadManifest:signal=>fetch('../model.json',{signal}).then(r=>{if(!r.ok)throw Error('manifest');return r.json();}),
    loadEnvironment:()=>new HDRLoader().loadAsync('../clearsky-softboxes.hdr'),
    loadFont:()=>document.fonts?.load('600 172px "JetBrains Mono"'),signal:scenePreparation.signal});
  if(disposed){hdr.dispose();return;}
  let pmrem;try{pmrem=new THREE.PMREMGenerator(renderer);environment=pmrem.fromEquirectangular(hdr);}finally{hdr.dispose();pmrem?.dispose();}
  scene.environment=environment.texture;scene.environmentIntensity=.65;
  assets=createMedalAssets(renderer,manifest);frameCache=new GalleryFrameCache(renderer,scene,camera,items);
  updateTiles();syncSelection();syncItems();requestDraw();
}catch{fail();}}
function scriptFailure(event){if(event.error)fail();}
function promiseFailure(){fail();}
addEventListener('error',scriptFailure);addEventListener('unhandledrejection',promiseFailure);
for(const [name,fn]of listeners)canvas.addEventListener(name,fn,name==='wheel'?{passive:false}:undefined);
function collectionChanged(){if(disposed)return;collectionLayout.invalidate();scrollSyncPending=true;requestDraw();}
const collectionResize=typeof ResizeObserver==='function'?new ResizeObserver(collectionChanged):null;
collectionResize?.observe(grid);document.fonts?.addEventListener('loadingdone',collectionChanged);
function onScroll(){scrollSyncPending=true;scrollMoving=true;clearTimeout(scrollSettleTimer);scrollSettleTimer=setTimeout(()=>{scrollMoving=false;requestDraw();},140);requestDraw();}
scroll.addEventListener('scroll',onScroll,{passive:true});addEventListener('resize',resize);addEventListener('pagehide',dispose);document.addEventListener('visibilitychange',visibility);
updateTiles();boot();
