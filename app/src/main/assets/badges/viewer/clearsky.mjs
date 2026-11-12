import * as THREE from './vendor/three.module.js';
import {GLTFLoader} from './vendor/GLTFLoader.js';
import {HDRLoader} from './vendor/HDRLoader.js';
import {RectAreaLightUniformsLib} from './vendor/RectAreaLightUniformsLib.js';

// Uses the approved Blender delivery unchanged. Only the signed personal label
// and host interaction remain in viewer.mjs; no identities enter this scene.
export async function createClearsky(renderer,scene,camera,isDisposed){
 const config=await fetch('../clearsky-scene.json').then(r=>{if(!r.ok)throw Error('Scene config unavailable');return r.json();});
 if(isDisposed())return null;
 const loader=new GLTFLoader();
 const results=await Promise.allSettled([
  loader.loadAsync('../clearsky-atelier.glb'),
  new HDRLoader().loadAsync('../clearsky-softboxes.hdr'),
  new THREE.TextureLoader().loadAsync('../clearsky-shadow.png')
 ]);
 function release(value){
  if(value.scene)value.scene.traverse(o=>{o.geometry?.dispose();for(const m of(Array.isArray(o.material)?o.material:[o.material]))if(m){for(const v of Object.values(m))if(v?.isTexture){v.dispose();v.source?.data?.close?.();}m.dispose();}});
  else value.dispose?.();
 }
 const failure=results.find(r=>r.status==='rejected');
 if(isDisposed()||failure){for(const r of results)if(r.status==='fulfilled')release(r.value);if(failure&&!isDisposed())throw failure.reason;return null;}
 const [space,hdr,shadow]=results.map(r=>r.value);
 // Attach allocations before preparing PMREM, so a later failure is cleaned by
 // the host's ordinary scene disposal as well.
 scene.add(space.scene);
 const contact=new THREE.Mesh(new THREE.PlaneGeometry(...config.contactShadow.size),
  new THREE.MeshBasicMaterial({map:shadow,transparent:true,opacity:config.contactShadow.opacity,depthWrite:false,toneMapped:false}));
 shadow.colorSpace=THREE.SRGBColorSpace;contact.position.fromArray(config.contactShadow.position);contact.rotation.x=-Math.PI/2;scene.add(contact);
 let environment,badgeEnvironment;
 const pmrem=new THREE.PMREMGenerator(renderer);
 try{
  hdr.mapping=THREE.EquirectangularReflectionMapping;pmrem.compileEquirectangularShader();environment=pmrem.fromEquirectangular(hdr);
  // A separate HDR studio reflection gives the metal dark intervals between
  // bright softboxes. The glass room continues using its original HDR.
  const width=512,height=256,data=new Float32Array(width*height*4);
  const boxes=[{x:.20,y:.36,sx:.055,sy:.20,p:3.8,c:[1,.82,.65]}, {x:.46,y:.28,sx:.13,sy:.085,p:2.8,c:[.82,.94,1]}, {x:.76,y:.43,sx:.035,sy:.18,p:3.5,c:[1,.95,.83]}, {x:.58,y:.72,sx:.18,sy:.08,p:.8,c:[.63,.85,.88]}];
  for(let y=0;y<height;y++)for(let x=0;x<width;x++){const i=(y*width+x)*4;let r=.075,g=.10,b=.12;for(const box of boxes){const dx=(x/width-box.x)/box.sx,dy=(y/height-box.y)/box.sy,v=box.p*Math.exp(-2*(dx*dx+dy*dy));r+=v*box.c[0];g+=v*box.c[1];b+=v*box.c[2];}data[i]=r;data[i+1]=g;data[i+2]=b;data[i+3]=1;}
  const studio=new THREE.DataTexture(data,width,height,THREE.RGBAFormat,THREE.FloatType);studio.mapping=THREE.EquirectangularReflectionMapping;studio.needsUpdate=true;
  try{badgeEnvironment=pmrem.fromEquirectangular(studio);}finally{studio.dispose();}
 }catch(error){environment?.dispose();badgeEnvironment?.dispose();throw error;}
 finally{hdr.dispose();pmrem.dispose();}
 renderer.setPixelRatio(Math.min(globalThis.devicePixelRatio||1,config.renderer.maxPixelRatio));
 renderer.transmissionResolutionScale=config.renderer.transmissionResolutionScale;
 renderer.toneMappingExposure=config.renderer.exposure;
 // A dedicated soft shadow is restricted to the badge meshes. The room geometry
 // does not cast or receive shadows; its separate contact shadow stays unchanged.
 renderer.shadowMap.enabled=Boolean(config.renderer.shadowMap);renderer.shadowMap.type=THREE.PCFSoftShadowMap;
 scene.background=new THREE.Color(config.palette.porcelain);scene.fog=null;
 scene.environment=environment.texture;scene.environmentIntensity=config.environment.intensity;scene.environmentRotation.y=config.environment.rotationY;
 RectAreaLightUniformsLib.init();
 for(const spec of config.lighting){const light=new THREE.RectAreaLight(spec.color,spec.intensity,spec.width,spec.height);light.position.fromArray(spec.position);light.lookAt(...spec.target);scene.add(light);}
 // Narrow softboxes add moving, directional reflections to the polished rim
 // and enamel as the original medal turns; the broad boxes light the room.
 const strip=new THREE.RectAreaLight(0xfff1d8,7,0.55,3.4);
 strip.position.set(-2.2,3.4,3.1);strip.lookAt(0,1.5,0);scene.add(strip);
 const edge=new THREE.RectAreaLight(0xb9ecff,5,0.4,2.6);
 edge.position.set(2.7,2.7,.8);edge.lookAt(0,1.5,0);scene.add(edge);
 scene.add(new THREE.HemisphereLight(0xe8f6ef,0x88b7bd,.18));
 // Rectangular lights do not cast shadows; this key gives the raised shapes
 // their own soft contact shadows without altering the source geometry.
 const detailKey=new THREE.DirectionalLight(0xfff5e6,1.55);
 detailKey.position.set(-3.4,5,4.5);detailKey.target.position.set(0,1.5,0);
 detailKey.castShadow=true;detailKey.shadow.mapSize.set(1024,1024);
 Object.assign(detailKey.shadow.camera,{left:-1.75,right:1.75,top:1.75,bottom:-1.75,near:.1,far:15});
 detailKey.shadow.camera.updateProjectionMatrix();
 detailKey.shadow.bias=-.00012;detailKey.shadow.normalBias=.003;detailKey.shadow.radius=2;
 scene.add(detailKey.target,detailKey);
 const layers=[];
 space.scene.traverse(o=>{if(o.isMesh){const m=o.material;if(m.transmission>0){m.thickness=o.userData.glassThickness||.065;m.attenuationDistance=3;m.envMapIntensity=.75;m.side=THREE.FrontSide;}if(o.name.includes('Seamless'))m.side=THREE.DoubleSide;
  if(o.name.startsWith('01__')||o.name.startsWith('02__')||o.name.startsWith('03__'))layers.push({object:o,origin:o.position.clone(),factor:o.name.startsWith('02__')?-.16:o.name.startsWith('03__')?.11:.20});
 }});
 // A restrained pool of light behind the glass adds depth without putting a
 // fake glare over the medal. It moves in the opposite direction to the veil.
 const glowCanvas=document.createElement('canvas');glowCanvas.width=glowCanvas.height=256;
 const glowContext=glowCanvas.getContext('2d'),gradient=glowContext.createRadialGradient(128,128,0,128,128,128);
 gradient.addColorStop(0,'rgba(255,250,231,.42)');gradient.addColorStop(.35,'rgba(211,248,240,.16)');gradient.addColorStop(1,'rgba(211,248,240,0)');
 glowContext.fillStyle=gradient;glowContext.fillRect(0,0,256,256);
 const glowTexture=new THREE.CanvasTexture(glowCanvas);glowTexture.colorSpace=THREE.SRGBColorSpace;
 const glow=new THREE.Sprite(new THREE.SpriteMaterial({map:glowTexture,transparent:true,depthWrite:false,blending:THREE.AdditiveBlending,opacity:.22}));
 glow.position.set(-.65,2.2,-1.5);glow.scale.set(4.4,4.4,1);scene.add(glow);
 camera.fov=config.camera.fov;camera.near=config.camera.near;camera.far=config.camera.far;camera.updateProjectionMatrix();
 const euler=new THREE.Euler(...config.badge.initialEulerDegrees.map(THREE.MathUtils.degToRad),'YXZ');
 const initialPose={q:new THREE.Quaternion().setFromEuler(euler).toArray(),zoom:1};
 function setQuality(interacting){
  // A finite pixel budget avoids full native resolution on large screens. Fine
  // detail is drawn after motion settles; active dragging stays responsive.
  const w=Math.max(1,globalThis.innerWidth||1),h=Math.max(1,globalThis.innerHeight||1);
  const maxTexture=renderer.capabilities.maxTextureSize||4096;
  const cap=Math.sqrt((interacting?1100000:3800000)/(w*h));
  const target=Math.max(.5,Math.min(globalThis.devicePixelRatio||1,interacting?1.25:3.5,cap,maxTexture/Math.max(w,h)));
  // Reduce the glass offscreen pass before balancing the canvas pixel budget.
  renderer.transmissionResolutionScale=interacting?.125:config.renderer.transmissionResolutionScale;
  renderer.shadowMap.enabled=Boolean(config.renderer.shadowMap)&&!interacting;
  if(Math.abs(renderer.getPixelRatio()-target)>.01)renderer.setPixelRatio(target);
 }
 return {environment,badgeEnvironment,diameter:config.badge.diameter,initialPose,setQuality,
  applyBadgeMaterial(material){if(material?.isMeshStandardMaterial){
   if(material.metalness>.55){material.envMap=badgeEnvironment.texture;material.envMapIntensity=1.35;material.roughness=Math.max(.14,Math.min(.4,material.roughness));}
   else material.envMapIntensity=Math.max(material.envMapIntensity??1,.9);
   if(material.clearcoat>0){material.clearcoat=Math.max(material.clearcoat,.55);material.clearcoatRoughness=Math.min(material.clearcoatRoughness??.1,.16);}
   material.needsUpdate=true;
  }},
  parallax(){return layers.map(layer=>[layer.object.position.x-layer.origin.x,layer.object.position.y-layer.origin.y]);},
  frame(root,control,focusY,reducedMotion){
   const detail=focusY!==0;
   if(!detail)control.zoom=THREE.MathUtils.clamp(control.zoom,config.badge.zoomMin,config.badge.zoomMax);
   const zoom=detail?1:control.zoom;root.scale.setScalar(1);root.position.fromArray(config.badge.position);
   if(Math.abs(camera.zoom-zoom)>.001){camera.zoom=zoom;camera.updateProjectionMatrix();}
   const distance=Math.max(config.camera.minDistance,config.badge.diameter/(2*Math.tan(THREE.MathUtils.degToRad(config.camera.fov/2))*camera.aspect*config.camera.fitBadgeWidth));
   camera.position.set(0,detail?focusY:config.camera.position[1],distance/(detail?control.zoom:1));camera.lookAt(0,detail?focusY:config.camera.target[1],0);
   const normal=new THREE.Vector3(0,0,1).applyQuaternion(root.quaternion);
   const initialNormal=new THREE.Vector3(0,0,1).applyQuaternion(new THREE.Quaternion().fromArray(initialPose.q));
   const px=reducedMotion?0:THREE.MathUtils.clamp(normal.x-initialNormal.x,-1,1),py=reducedMotion?0:THREE.MathUtils.clamp(normal.y-initialNormal.y,-1,1);
   for(const layer of layers){layer.object.position.x=layer.origin.x+px*layer.factor;layer.object.position.y=layer.origin.y+py*layer.factor*.55;}
   glow.position.x=-.65-px*.22;glow.position.y=2.2-py*.12;
   contact.scale.set(.72+.28*Math.abs(normal.z),1,1);contact.material.opacity=config.contactShadow.opacity;
  },sceneMeshes:5};
}
