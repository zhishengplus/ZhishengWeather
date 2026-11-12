import {fitStage} from './presentation-core.mjs';
/* One physical medal, one camera, one clean studio. Native UI is a restrained overlay. */
import * as THREE from './vendor/three.module.js';
import {GLTFLoader} from './vendor/GLTFLoader.js';
import {HDRLoader} from './vendor/HDRLoader.js';
import {BadgeTrackball} from './trackball.mjs';
import {createMedalAssets} from './medal-assets.mjs';
import {modelKey,instanceKey,validDisplay} from './model-registry.mjs';
import {BadgeFaceFeedback} from './feedback.mjs';
import {cabinetFrustum,heroFrustum,cabinetDisplays} from './cabinet-layout-runtime.mjs';
import {CabinetFlight,ease,bezier,renderScale} from './cabinet-flight.mjs';

const canvas=document.getElementById('view'),status=document.getElementById('status');
const control=new BadgeTrackball(3.2),flight=new CabinetFlight(),face=new BadgeFaceFeedback();
let renderer,scene,camera,layout,assets,manifest,environment,selected,key,fill,rim;
let config={dark:false,serial:null,inspection:null,slots:[],...globalThis.__BADGE_CONFIG__};
let disposed=false,active=true,frame=0,last=0,ready=false,booted=false,firstFrameMillis=null,pixelRatio=0,phase='',lastPose=0;
let reconcileGeneration=0,modelsSettled=false;
let items=new Map(),ownedResources=new Set(),returnQ=new THREE.Quaternion(),requestKey=null,userPoseRevision=0;
const started=performance.now(),homeCamera=new THREE.Vector3(),homeTarget=new THREE.Vector3(),identity=new THREE.Quaternion();
const focusPoint=new THREE.Vector3(0,3.9,5.5),focusCamera=new THREE.Vector3(0,3.9,11.3),focusLook=new THREE.Vector3(0,3.55,5.5);
const feedback=event=>{if(active&&!disposed&&!config.reducedMotion)try{globalThis.BadgeHost?.haptic(event);}catch{}};
const notify=value=>{if(!disposed)try{globalThis.BadgeHost?.status(value);}catch{}};
function applyStageBackground(){
 // The native page owns the continuous studio gradient; the WebView stays transparent.
 document.body.style.background='transparent';
}
function disposeObject(object){object?.traverse(o=>{if(o.geometry)ownedResources.add(o.geometry);for(const m of Array.isArray(o.material)?o.material:[o.material])if(m){for(const v of Object.values(m))if(v?.isTexture)ownedResources.add(v);ownedResources.add(m);}});}
function flushResources(){for(const r of ownedResources){r.dispose?.();r.source?.data?.close?.();}ownedResources.clear();}
function requestDraw(){if(booted&&active&&!disposed&&renderer&&!frame)frame=requestAnimationFrame(draw);}
function quality(moving){
 const w=Math.max(1,Math.round(innerWidth)),h=Math.max(1,Math.round(innerHeight));
 const ratio=renderScale(w,h,devicePixelRatio,moving,renderer.capabilities.maxTextureSize);
 if(Math.abs(ratio-pixelRatio)>.01||canvas.clientWidth!==w||canvas.clientHeight!==h){pixelRatio=ratio;canvas.style.width=w+'px';canvas.style.height=h+'px';renderer.setPixelRatio(ratio);renderer.setSize(w,h,false);}
 control.width=w;control.height=h;
}
function resize(){if(disposed||!renderer)return;control.stop();quality(false);requestDraw();}
async function reconcile(){if(!assets||!layout||disposed)return;
 const revision=++reconcileGeneration,wanted=cabinetDisplays(config.slots),requested=validDisplay(config.inspection)?{...config.inspection,slot:1,assetKey:modelKey(config.inspection)}:null;
 const need=[...new Map([...wanted,...(requested?[requested]:[])].map(v=>[modelKey(v),v])).values()];
 if(need.some(value=>!assets.has(modelKey(value)))){status.textContent='正在准备徽章';status.hidden=false;}
 try{await Promise.all(need.map(value=>assets.ensure(value)));}catch{if(revision===reconcileGeneration&&!disposed)fail();return;}
 if(disposed||revision!==reconcileGeneration)return;modelsSettled=true;status.hidden=true;
 const wantedKeys=new Set(wanted.map(instanceKey));
 for(const[key,item]of items)if(!wantedKeys.has(key)&&item!==selected){assets.releaseInstance(item);items.delete(key);}
 for(const value of wanted){const id=instanceKey(value);let item=items.get(id);
  if(item&&item.assetKey!==modelKey(value)&&item!==selected){assets.releaseInstance(item);items.delete(id);item=null;}
  if(!item){item=assets.instance(value,layout,config.dark);scene.add(item.object);items.set(id,item);}
  else if(item!==selected){const slot=layout.slots[value.slot];item.home.fromArray(slot.badgeBottomCenter);item.home.y+=item.heightRatio*slot.targetVisualWidth/2;item.slot=value.slot;item.width=slot.targetVisualWidth;item.object.position.copy(item.home);item.object.scale.setScalar(item.width);}}
 if(requested&&requestKey!==instanceKey(requested))focus(requested);else if(!requested&&requestKey!=null)back();
 assets.trim([...items.values()].map(item=>item.assetKey));requestDraw();
}
function focus(value){if(disposed||!active||!assets||!validDisplay(value)||!assets.has(modelKey(value)))return;if(flight.phase!=='overview')return;
 const key=instanceKey(value);let item=items.get(key);if(!item){item=assets.instance(value,layout,config.dark,true);scene.add(item.object);items.set(key,item);}selected=item;requestKey=key;
 control.reset();face.reset();returnQ.identity();flight.enter();last=0;feedback('touch');requestDraw();
}
function back(){if(!selected||disposed)return;requestKey=null;control.stop();face.reset();returnQ.fromArray(control.q);flight.back();last=0;requestDraw();}
function emitPose(now,force=false){if(!selected)return;if(force||now-lastPose>120){lastPose=now;try{globalThis.BadgeHost?.pose(JSON.stringify(control.pose()));}catch{}}}
function draw(now){frame=0;if(!active||disposed||!renderer||!booted||!scene||!camera||!key)return;
 const dt=last?Math.min(250,Math.max(0,now-last)):0;last=now;const was=flight.phase;flight.tick(dt,config.reducedMotion);
 const moving=flight.moving||(flight.phase==='inspect'&&control.tick(dt/1000))||control.pointers.size>0;
 renderer.transmissionResolutionScale=moving?.25:.5;quality(moving);const t=ease(flight.progress),aspect=Math.max(1,innerWidth)/Math.max(1,innerHeight),home=config.slots.length===1?heroFrustum(innerWidth,innerHeight):cabinetFrustum(innerWidth,innerHeight),homeSpan=home.top*2,focusSpan=config.slots.length===1?Math.max(2.2,2.2/aspect):Math.max(3.0,2.18/aspect);
 if(selected){
  control.maxZoom=selected.presentation.maxZoom;const a=selected.home.toArray(),d=focusPoint.toArray(),b=[a[0]*.9,a[1]+.65,a[2]+1.25],c=[0,d[1]+.2,d[2]-1.1];selected.object.position.fromArray(bezier(a,b,c,d,t));selected.object.scale.setScalar(THREE.MathUtils.lerp(selected.width,1.78,t));
  if(flight.phase==='inspect')selected.object.quaternion.fromArray(control.q);else if(flight.target===0)selected.object.quaternion.copy(identity).slerp(returnQ,t);else selected.object.quaternion.identity();
 }
 if(config.slots.length===1){const hero=selected??items.values().next().value;if(hero){homeTarget.copy(hero.home);homeCamera.copy(homeTarget).add(new THREE.Vector3(0,0,7));}}focusLook.copy(focusPoint);camera.position.lerpVectors(homeCamera,focusCamera,t);camera.lookAt(new THREE.Vector3().lerpVectors(homeTarget,focusLook,t));
 const hero=selected??items.values().next().value;const fit=hero?fitStage(hero.radius*hero.width,innerWidth,innerHeight,hero.presentation.occupancy):null;const nearFit=selected?fitStage(selected.radius*1.78,innerWidth,innerHeight,selected.presentation.occupancy):null;const span=THREE.MathUtils.lerp(config.slots.length===1&&fit?fit.top*2:homeSpan,nearFit?nearFit.top*2:focusSpan,t);camera.left=-span*aspect/2;camera.right=span*aspect/2;camera.top=span/2;camera.bottom=-span/2;camera.zoom=1+(control.zoom-1)*t;camera.updateProjectionMatrix();
 key.intensity=THREE.MathUtils.lerp(1.45,1.6,t);fill.intensity=.5;rim.intensity=1.15;
 try{renderer.render(scene,camera);}catch{fail();return;}
 if(!ready&&modelsSettled){ready=true;firstFrameMillis=Math.round(performance.now()-started);status.hidden=true;notify('ready');}
 if(phase!==flight.phase){phase=flight.phase;if(phase==='inspect'){feedback('entry');notify('ready');}else if(phase==='overview'&&was!=='overview'){feedback('reset');if(selected?.transient){assets.releaseInstance(selected);items.delete(instanceKey(selected));}selected=null;control.reset();reconcile();}}
 if(flight.phase==='inspect'){emitPose(now);const f=face.sample(control.q,now);if(f)feedback(f);}
 if(moving||flight.moving||face.pending())requestDraw();else if(pixelRatio<Math.min(devicePixelRatio||1,3.25)&&dt>0){quality(false);renderer.render(scene,camera);}
}
function pause(){active=false;flight.pause();control.stop();if(frame)cancelAnimationFrame(frame);frame=0;last=0;emitPose(performance.now(),true);}
function resume(){if(disposed)return;active=true;flight.resume();last=0;resize();}
function dispose(){if(disposed)return;pause();disposed=true;flight.dispose();removeEventListener('resize',resize);document.removeEventListener('visibilitychange',visibility);removeEventListener('pagehide',dispose);
 for(const [name,handler]of listeners)canvas.removeEventListener(name,handler);disposeObject(scene);assets?.dispose();flushResources();environment?.dispose();renderer?.dispose();renderer?.forceContextLoss();items.clear();}
function fail(){if(disposed)return;status.hidden=false;status.textContent='三维场景暂不可用，请使用收藏列表';notify('render_failed');dispose();}
function visibility(){if(document.hidden)pause();else resume();}
let downPoint=null;
function pointerDown(e){if(!active||disposed)return;e.preventDefault();if(flight.phase==='overview'){downPoint={id:e.pointerId,x:e.offsetX,y:e.offsetY};return;}if(flight.phase!=='inspect')return;control.down(e.pointerId,e.offsetX,e.offsetY,e.timeStamp);canvas.setPointerCapture(e.pointerId);feedback('touch');last=0;requestDraw();}
function pointerMove(e){if(downPoint&&Math.hypot(e.offsetX-downPoint.x,e.offsetY-downPoint.y)>8)downPoint=null;if(flight.phase!=='inspect')return;if(control.move(e.pointerId,e.offsetX,e.offsetY,e.timeStamp)){e.preventDefault();userPoseRevision++;control.velocity=control.velocity.map(v=>v*.62);requestDraw();}}
function pointerUp(e){if(flight.phase==='overview'&&downPoint?.id===e.pointerId&&e.type==='pointerup'){const rect=canvas.getBoundingClientRect(),ray=new THREE.Raycaster();ray.setFromCamera(new THREE.Vector2((e.clientX-rect.left)/rect.width*2-1,-(e.clientY-rect.top)/rect.height*2+1),camera);
  const hit=ray.intersectObjects([...items.values()].map(v=>v.object),true)[0];if(hit){let o=hit.object;while(o&&!o.userData.instanceKey)o=o.parent;const item=items.get(o?.userData.instanceKey);if(item)try{globalThis.BadgeHost?.selectSlot(item.slot);}catch{}}}
 downPoint=null;control.up(e.pointerId,e.timeStamp);if(e.type!=='pointerup'||config.reducedMotion)control.stop();requestDraw();}
function wheel(e){if(flight.phase!=='inspect')return;e.preventDefault();control.zoomBy(Math.exp(-e.deltaY*.001));requestDraw();}
function keydown(e){const actions={Escape:back,ArrowLeft:()=>api.rotate([0,1,0],-.2),ArrowRight:()=>api.rotate([0,1,0],.2),ArrowUp:()=>api.rotate([1,0,0],-.2),ArrowDown:()=>api.rotate([1,0,0],.2),'+':()=>api.zoom(1.15),'-':()=>api.zoom(1/1.15),'0':()=>api.reset()};if(actions[e.key]){e.preventDefault();actions[e.key]();}}
function contextLost(e){e.preventDefault();fail();}
const listeners=[['pointerdown',pointerDown],['pointermove',pointerMove],['pointerup',pointerUp],['pointercancel',pointerUp],['lostpointercapture',pointerUp],['wheel',wheel],['keydown',keydown],['webglcontextlost',contextLost]];
const api=globalThis.BadgeView={
 configure(next){if(disposed)return;const wasDark=config.dark;config={...config,...next};if(wasDark!==config.dark)applyStageBackground();reconcile();},pause,resume,dispose,
 reset(){if(flight.phase!=='inspect')return;control.reset();face.reset();requestDraw();feedback('reset');},
 rotate(axis,angle){if(flight.phase!=='inspect')return;control.rotate(axis,angle);requestDraw();},
 zoom(factor){if(flight.phase!=='inspect')return;control.zoomBy(factor);requestDraw();},
 detail(){if(flight.phase!=='inspect')return;control.setPose({q:[0,1,0,0],zoom:2.1});requestDraw();},
 diagnostics(){return {modelLoaded:!!assets&&modelsSettled,labelReady:modelsSettled,serial:selected?.serial??null,labelSerial:selected?.label.userData.serial??null,active,disposed,pendingFrame:!!frame,firstFrame:ready,firstFrameMillis,sceneStyle:'cabinet',phase:flight.phase,transitionProgress:flight.progress,
  slotSerials:[...items.values()].filter(v=>!v.transient).map(v=>({slot:v.slot,serial:v.serial,badgeId:v.badgeId})),realMedalCount:items.size,pixelRatio,shadowEnabled:false,userPoseRevision,activePointers:control.pointers.size,pose:control.pose(),drawCalls:renderer?.info.render.calls,triangles:renderer?.info.render.triangles,geometryCount:renderer?.info.memory.geometries,textureCount:renderer?.info.memory.textures,contextLost:renderer?.getContext().isContextLost()??false};}
};
async function boot(){try{
 if(!canvas.getContext('webgl2',{alpha:true,antialias:true})){notify('graphics_unavailable');status.textContent='当前设备使用收藏列表';return;}
 renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'high-performance'});renderer.setClearColor(0x000000,0);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=.90;renderer.shadowMap.enabled=false;
 scene=new THREE.Scene();camera=new THREE.OrthographicCamera(-3,3,4,-4,.1,100);applyStageBackground();
 const [l,m,hdr]=await Promise.all([fetch('../cabinet-layout.json').then(r=>{if(!r.ok)throw Error('layout');return r.json();}),fetch('../model.json').then(r=>{if(!r.ok)throw Error('manifest');return r.json();}),new HDRLoader().loadAsync('../clearsky-softboxes.hdr')]);
 if(disposed){hdr.dispose();return;}layout=l;manifest=m;
 if(!manifest.available||!/^[a-zA-Z0-9_.-]+\.glb$/.test(manifest.model))throw Error('Unavailable model');homeCamera.fromArray(layout.camera.position);homeTarget.fromArray(layout.camera.target);
 scene.add(new THREE.HemisphereLight(0xe4fff5,0x263936,.28));key=new THREE.DirectionalLight(0xffedcf,3.2);key.position.set(-3,8,6);scene.add(key);fill=new THREE.DirectionalLight(0xe1f4ff,1.5);fill.position.set(4,4,4);scene.add(fill);rim=new THREE.DirectionalLight(0xd6fff0,2.1);rim.position.set(1,5,-3);scene.add(rim);
 let pmrem;
 try{hdr.mapping=THREE.EquirectangularReflectionMapping;pmrem=new THREE.PMREMGenerator(renderer);environment=pmrem.fromEquirectangular(hdr);}finally{hdr.dispose();pmrem?.dispose();}
 scene.environment=environment.texture;scene.environmentIntensity=.65;
 assets=createMedalAssets(renderer,manifest);booted=true;
 await document.fonts?.load('600 172px "JetBrains Mono"');if(disposed){assets?.dispose();flushResources();environment?.dispose();return;}reconcile();resize();requestDraw();
 }catch{fail();}}
for(const [name,handler]of listeners)canvas.addEventListener(name,handler,name==='wheel'?{passive:false}:undefined);
addEventListener('resize',resize);document.addEventListener('visibilitychange',visibility);addEventListener('pagehide',dispose);boot();
