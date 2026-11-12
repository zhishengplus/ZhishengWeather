import * as THREE from './vendor/three.module.js';
import {GLTFLoader} from './vendor/GLTFLoader.js';
import {RoomEnvironment} from './vendor/RoomEnvironment.js';
import {BadgeTrackball} from './trackball.mjs';
import {paintLabel,validateAnchor} from './label.mjs';
import {BadgeFaceFeedback} from './feedback.mjs';
import {BadgeReveal} from './reveal.mjs';

const canvas=document.getElementById('view'),status=document.getElementById('status'),control=new BadgeTrackball(globalThis.__BADGE_SCENE__==='clearsky'?3.2:2.5);
let renderer,scene,camera,root,model,environment,label,manifest,frame=0,last=0,active=true,disposed=false,lastPose=0;
let config={serial:null,dark:false,pose:null,...globalThis.__BADGE_CONFIG__};
let removedSignatureTriangles=0;
let userPoseRevision=0;
let faceArmed=false;
let focusY=0;
const faceFeedback=new BadgeFaceFeedback();
const reveal=new BadgeReveal();
let studioLights=[];
let clearsky=null;
let firstFrame=false,readyCode='ready',shaderFailed=false,firstFrameMillis=null;
const bootStarted=performance.now();
function cancelReveal(){const q=reveal.cancel();if(q)control.setPose({...control.pose(),q});}
function tuneStudio(){
  if(!renderer||studioLights.length!==3)return;
  renderer.toneMappingExposure=config.dark ? .96 : 1.0;
  studioLights[0].intensity=config.dark?2.8:3.2;
  studioLights[1].intensity=config.dark?1.2:2.2;
  studioLights[2].intensity=config.dark?3.2:2.4;
}
function feedback(event){if(!active||disposed||config.reducedMotion)return;try{globalThis.BadgeHost?.haptic(event);}catch{}}
function notify(value){try{globalThis.BadgeHost?.status(value);}catch{}}
function message(text,code){status.textContent=text;status.hidden=false;notify(code);}
function poseChanged(force=false){const now=performance.now();if(force||now-lastPose>100){lastPose=now;try{globalThis.BadgeHost?.pose(JSON.stringify(control.pose()));}catch{}}}
function draw(now){frame=0;if(disposed||!active||!renderer||!root)return;
  const entrance=reveal.tick(now);
  const moving=entrance?true:last?control.tick((now-last)/1000):false;last=now;
  root.quaternion.fromArray(entrance||control.q);
  if(entrance&&!reveal.active)control.setPose({...control.pose(),q:entrance});
  if(clearsky){clearsky.setQuality(!!entrance||moving||control.pointers.size>0);clearsky.frame(root,control,focusY,config.reducedMotion);}
  else{
  const fittedDistance=Math.max(3.1,1.1/(Math.tan(Math.PI/10)*camera.aspect));
  camera.position.set(0,focusY,fittedDistance/control.zoom);camera.lookAt(0,focusY,0);
  }
  try { renderer.render(scene,camera); } catch { message('三维显示暂时不可用','render_failed');dispose();return; }
  if(shaderFailed)return;
  if(!firstFrame&&model&&canvas.clientWidth>0&&canvas.clientHeight>0){firstFrame=true;firstFrameMillis=Math.round(performance.now()-bootStarted);notify(readyCode);}
  poseChanged();
  if(faceArmed){const landed=faceFeedback.sample(control.q,now);if(landed)feedback(landed);}
  if(moving||(faceArmed&&faceFeedback.pending()))requestDraw();
}
function requestDraw(){if(!frame&&active&&!disposed)frame=requestAnimationFrame(draw);}
function resize(){
  // Android WebView can resolve an initial 100%-height canvas to zero even
  // after the viewport is visible. The canvas must not depend on that chain.
  const width=Math.max(1,Math.round(globalThis.innerWidth||document.documentElement?.clientWidth||canvas.getBoundingClientRect().width||1));
  const height=Math.max(1,Math.round(globalThis.innerHeight||document.documentElement?.clientHeight||canvas.getBoundingClientRect().height||1));
  if(canvas.style.width!==width+'px')canvas.style.setProperty('width',width+'px','important');
  if(canvas.style.height!==height+'px')canvas.style.setProperty('height',height+'px','important');
  control.resize(width,height);
  if(renderer){renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();requestDraw();}
}
function disposeMaterial(material,seen){if(!material||seen.has(material))return;seen.add(material);for(const value of Object.values(material))if(value?.isTexture&&!seen.has(value)){seen.add(value);value.dispose();value.source?.data?.close?.();}material.dispose();}
function disposeObject(object){const seen=new Set();object?.traverse(child=>{if(child.geometry&&!seen.has(child.geometry)){seen.add(child.geometry);child.geometry.dispose();}for(const m of(Array.isArray(child.material)?child.material:[child.material]))disposeMaterial(m,seen);});}
function replaceOriginalSignature(object,box){
  if(!box||!Array.isArray(box.min)||!Array.isArray(box.max))return;
  object.updateMatrixWorld(true);const point=new THREE.Vector3();
  object.traverse(child=>{
    if(!child.isMesh||child.material?.name!==box.material||!child.geometry.index)return;
    const geometry=child.geometry,positions=geometry.getAttribute('position'),indices=geometry.index.array,kept=[];
    const inside=i=>{point.fromBufferAttribute(positions,i).applyMatrix4(child.matrixWorld);return point.toArray().every((x,k)=>x>=box.min[k]&&x<=box.max[k]);};
    for(let i=0;i<indices.length;i+=3){if(inside(indices[i])&&inside(indices[i+1])&&inside(indices[i+2]))removedSignatureTriangles++;else kept.push(indices[i],indices[i+1],indices[i+2]);}
    geometry.setIndex(kept);
  });
}
function updateLabel(){
  if(!model||!manifest)return;
  if(label){label.parent.remove(label);disposeObject(label);label=null;}
  if(!validateAnchor(manifest.labelAnchor)){message('编号标牌尚未完成模型校准','label_uncalibrated');return;}
  const anchor=manifest.labelAnchor,texture=new THREE.CanvasTexture(paintLabel(document.createElement('canvas'),config.serial,config.dark));texture.colorSpace=THREE.SRGBColorSpace;texture.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());
  const material=new THREE.MeshStandardMaterial({map:texture,transparent:true,metalness:.38,roughness:.54,envMapIntensity:.7,depthWrite:false,polygonOffset:true,polygonOffsetFactor:-3,polygonOffsetUnits:-2});
  // Dynamic high-resolution surface lettering, not per-user GLB geometry or true cut-through engraving.
  label=new THREE.Mesh(new THREE.PlaneGeometry(anchor.width*.94,anchor.height*.92),material);
  label.userData.serial=config.serial;label.renderOrder=3;
  label.position.fromArray(anchor.position);
  const normal=new THREE.Vector3().fromArray(anchor.normal).normalize(),up=new THREE.Vector3().fromArray(anchor.up).normalize();
  const right=new THREE.Vector3().crossVectors(up,normal).normalize();up.crossVectors(normal,right).normalize();
  label.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(right,up,normal));model.add(label);
}
async function boot(){
  try{
    manifest=await fetch('../model.json',{cache:'no-store'}).then(r=>r.json());
    if(disposed)return; // A page closed during the asynchronous manifest read must not allocate GPU resources.
    if(!manifest.available){message('三维素材尚未就绪','asset_unavailable');return;}
    if(typeof manifest.model!=='string'||!manifest.model.match(/^[a-zA-Z0-9_.-]+\.glb$/))throw new Error('Invalid local asset');
    if(!canvas.getContext('webgl2',{alpha:true,antialias:true})){message('当前图形能力不足，使用静态展示','graphics_unavailable');return;}
    renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'low-power'});
    renderer.debug.onShaderError=()=>{shaderFailed=true;queueMicrotask(()=>{message('三维显示暂时不可用','render_failed');dispose();});};
    renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.7));renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;
    renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFShadowMap;
    scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(36,1,.01,100);
    if(globalThis.__BADGE_SCENE__==='clearsky'){
      const adapter=await import('./clearsky.mjs');if(disposed)return;
      clearsky=await adapter.createClearsky(renderer,scene,camera,()=>disposed);
      if(disposed||!clearsky)return;environment=clearsky.environment;
    }else{
    scene.fog=new THREE.Fog(0x061218,9,28);
    const room=new RoomEnvironment(),pmrem=new THREE.PMREMGenerator(renderer);environment=pmrem.fromScene(room,.04);scene.environment=environment.texture;disposeObject(room);pmrem.dispose();
    scene.add(new THREE.HemisphereLight(0xf3fff9,0x3c5a6a,.85));
    const key=new THREE.DirectionalLight(0xfff4df,3.2);key.position.set(-3,5,4);scene.add(key);
    key.castShadow=true;key.shadow.mapSize.set(512,512);key.shadow.radius=5;key.shadow.camera.left=-3;key.shadow.camera.right=3;
    key.shadow.camera.top=3;key.shadow.camera.bottom=-3;key.shadow.camera.near=.5;key.shadow.camera.far=16;key.shadow.bias=-.00025;
    const fill=new THREE.DirectionalLight(0xcce7ff,2.0);fill.position.set(4,1,3);scene.add(fill);
    const rim=new THREE.DirectionalLight(0xc9ffe9,2.6);rim.position.set(1,3,-4);scene.add(rim);
    studioLights=[key,fill,rim];tuneStudio();
    // A real, fog-softened studio floor anchors the floating metal object.
    // The deterministic fine grain avoids a flat colored backdrop or visible tiling.
    const grainCanvas=document.createElement('canvas');grainCanvas.width=grainCanvas.height=128;
    const grainContext=grainCanvas.getContext('2d'),grain=grainContext.createImageData(128,128);
    let seed=1234567;
    for(let i=0;i<grain.data.length;i+=4){seed=(Math.imul(seed,1664525)+1013904223)>>>0;const n=222+(seed>>>28);grain.data[i]=grain.data[i+1]=grain.data[i+2]=n;grain.data[i+3]=255;}
    grainContext.putImageData(grain,0,0);const grainTexture=new THREE.CanvasTexture(grainCanvas);
    grainTexture.wrapS=grainTexture.wrapT=THREE.RepeatWrapping;grainTexture.repeat.set(80,80);grainTexture.colorSpace=THREE.SRGBColorSpace;
    const floorMaterial=new THREE.MeshBasicMaterial({color:0x233b41,map:grainTexture,transparent:true,opacity:.75,depthWrite:false});
    floorMaterial.onBeforeCompile=shader=>{
      shader.vertexShader=shader.vertexShader.replace('#include <common>','#include <common>\n varying float badgeFloorDepth;')
        .replace('#include <project_vertex>','#include <project_vertex>\n badgeFloorDepth = -mvPosition.z;');
      shader.fragmentShader=shader.fragmentShader.replace('#include <common>','#include <common>\n varying float badgeFloorDepth;')
        .replace('#include <fog_fragment>','#include <fog_fragment>\n gl_FragColor.a *= 1.0 - smoothstep(5.0, 18.0, badgeFloorDepth);');
    };
    const floor=new THREE.Mesh(new THREE.PlaneGeometry(160,160),floorMaterial);
    floor.rotation.x=-Math.PI/2;floor.position.y=-1.08;scene.add(floor);
    const contact=new THREE.Mesh(new THREE.PlaneGeometry(160,160),new THREE.ShadowMaterial({color:0x000000,opacity:.32,depthWrite:false}));
    contact.rotation.x=-Math.PI/2;contact.position.y=-1.077;contact.receiveShadow=true;scene.add(contact);
    }
    root=new THREE.Group();scene.add(root);
    const gltf=await new GLTFLoader().loadAsync('../'+manifest.model);
    if(disposed){disposeObject(gltf.scene);return;}
    await document.fonts?.load('600 172px "JetBrains Mono"');
    if(disposed){disposeObject(gltf.scene);return;}
    model=gltf.scene;
    model.traverse(child=>{for(const material of(Array.isArray(child.material)?child.material:[child.material])){
      if(material?.isMeshStandardMaterial){
        for(const value of Object.values(material))if(value?.isTexture)value.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());
        if(material.metalness>.55){material.roughness=Math.max(.14,Math.min(.4,material.roughness));material.envMapIntensity=1.25;}
        else material.envMapIntensity=Math.max(material.envMapIntensity??1,.9);
        if(material.clearcoat>0){material.clearcoat=Math.max(material.clearcoat,.55);material.clearcoatRoughness=Math.min(material.clearcoatRoughness??.1,.16);}
        material.needsUpdate=true;
      }
      if(clearsky)clearsky.applyBadgeMaterial(material);
      if(child.isMesh){child.castShadow=true;if(clearsky)child.receiveShadow=true;}
    }});
    replaceOriginalSignature(model,manifest.replaceSignatureBox);updateLabel();
    const bounds=new THREE.Box3().setFromObject(model),size=bounds.getSize(new THREE.Vector3()),center=bounds.getCenter(new THREE.Vector3());
    const group=new THREE.Group();group.add(model);model.position.sub(center);group.scale.setScalar((clearsky?.diameter||1.65)/Math.max(size.x,size.y,size.z));root.add(group);
    if(config.pose)control.setPose(config.pose);else if(clearsky)control.setPose(clearsky.initialPose);status.hidden=validateAnchor(manifest.labelAnchor);readyCode=status.hidden?'ready':'label_uncalibrated';resize();feedback('entry');
  }catch{message('三维视图暂时不可用，可返回后重试','render_failed');dispose();}
}
function dispose(){if(disposed)return;disposed=true;active=false;control.stop();if(frame)cancelAnimationFrame(frame);frame=0;observer.disconnect();window.removeEventListener('resize',resize);disposeObject(scene);environment?.dispose();clearsky?.badgeEnvironment?.dispose();renderer?.dispose();renderer?.forceContextLoss();renderer=null;}
globalThis.BadgeView={
  configure(next){if(disposed)return;const labelChanged=('serial'in next&&next.serial!==config.serial)||('dark'in next&&next.dark!==config.dark);config={...config,...next};if(config.reducedMotion)cancelReveal();if(next.pose)control.setPose(next.pose);if(labelChanged)updateLabel();tuneStudio();requestDraw();},
  reveal(){if(!root||!active||disposed)return;control.stop();reveal.start(control.pose(),performance.now(),config.reducedMotion);last=0;requestDraw();},
  pause(){cancelReveal();active=false;faceArmed=false;control.stop();if(frame)cancelAnimationFrame(frame);frame=0;last=0;poseChanged(true);},
  resume(){if(!disposed){active=true;last=0;resize();requestDraw();}},
  reset(){cancelReveal();focusY=0;if(clearsky)control.setPose(clearsky.initialPose);else control.reset();faceArmed=false;faceFeedback.reset();last=0;poseChanged(true);requestDraw();feedback('reset');},
  detail(){cancelReveal();if(!label||!root)return;control.setPose({q:[0,1,0,0],zoom:2.1});root.quaternion.fromArray(control.q);scene.updateMatrixWorld(true);focusY=label.getWorldPosition(new THREE.Vector3()).y;faceArmed=false;last=0;poseChanged(true);requestDraw();feedback('reset');},
  rotate(axis,angle){cancelReveal();focusY=0;faceArmed=true;control.rotate(axis,angle);poseChanged(true);requestDraw();},
  zoom(factor){cancelReveal();control.zoomBy(factor);poseChanged(true);requestDraw();},
  diagnostics(){return{pose:control.pose(),activePointers:control.pointers.size,userPoseRevision,removedSignatureTriangles,modelLoaded:!!model,labelReady:!!label,serial:config.serial,labelSerial:label?.userData.serial??null,active,disposed,pendingFrame:!!frame,revealActive:reveal.active,firstFrame,firstFrameMillis,shaderFailed,pixelRatio:renderer?.getPixelRatio()??null,shadowEnabled:!!renderer?.shadowMap.enabled,sceneStyle:clearsky?'clearsky':'classic',sceneMeshes:clearsky?.sceneMeshes||0,sceneParallax:clearsky?.parallax()??null,viewport:{width:canvas.clientWidth,height:canvas.clientHeight},contextLost:renderer?.getContext().isContextLost()??false};},
  dispose
};
canvas.addEventListener('pointerdown',e=>{e.preventDefault();cancelReveal();focusY=0;faceArmed=true;control.down(e.pointerId,e.offsetX,e.offsetY,e.timeStamp);canvas.setPointerCapture(e.pointerId);last=0;requestDraw();feedback('touch');});
canvas.addEventListener('pointermove',e=>{if(control.move(e.pointerId,e.offsetX,e.offsetY,e.timeStamp)){userPoseRevision++;e.preventDefault();requestDraw();}});
for(const event of['pointerup','pointercancel','lostpointercapture'])canvas.addEventListener(event,e=>{control.up(e.pointerId,e.timeStamp);if(config.reducedMotion||event==='pointercancel')control.stop();requestDraw();});
canvas.addEventListener('wheel',e=>{e.preventDefault();control.zoomBy(Math.exp(-e.deltaY*.001));requestDraw();},{passive:false});
canvas.addEventListener('keydown',e=>{const actions={ArrowLeft:()=>BadgeView.rotate([0,1,0],-.2),ArrowRight:()=>BadgeView.rotate([0,1,0],.2),ArrowUp:()=>BadgeView.rotate([1,0,0],-.2),ArrowDown:()=>BadgeView.rotate([1,0,0],.2),'+':()=>BadgeView.zoom(1.15),'-':()=>BadgeView.zoom(1/1.15),'0':()=>BadgeView.reset()};if(actions[e.key]){e.preventDefault();actions[e.key]();requestDraw();}});
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();message('图形资源已释放，使用静态展示','graphics_unavailable');dispose();});
document.addEventListener('visibilitychange',()=>document.hidden?globalThis.BadgeView.pause():globalThis.BadgeView.resume());
window.addEventListener('pagehide',dispose,{once:true});
const observer=new ResizeObserver(resize);observer.observe(document.documentElement);window.addEventListener('resize',resize);boot();
