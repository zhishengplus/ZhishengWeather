import {applyModelMaterialPolicy} from './material-policy.mjs';
import {createRingStudio} from './ring-studio.mjs';
import {engraveRing} from './ring-engraving.mjs';
import {cleanRingSurface} from './ring-surface.mjs';
import {boundsMetrics,isolatedTriangleMask} from './presentation-core.mjs';
import * as THREE from './vendor/three.module.js';
import {loadBadgeModel} from './model-loader.mjs';
import {modelSpec,modelKey,instanceKey} from './model-registry.mjs';
import {engraveHonor} from './honor-engraving.mjs';
import {paintLabel,serialLabel,validateAnchor} from './label.mjs';

function destroy(object,seen=new Set()){object?.traverse(o=>{if(o.geometry&&!seen.has(o.geometry)){seen.add(o.geometry);o.geometry.dispose();}for(const m of Array.isArray(o.material)?o.material:[o.material])if(m&&!seen.has(m)){seen.add(m);for(const v of Object.values(m))if(v?.isTexture&&!seen.has(v)&&!(m.userData.sharedStudioEnvironment&&v===m.envMap)){seen.add(v);v.dispose();v.source?.data?.close?.();}m.dispose();}});}
function stripOldSignature(model,box){if(!box)return;model.updateMatrixWorld(true);const point=new THREE.Vector3();model.traverse(o=>{if(!o.isMesh||o.material?.name!==box.material||!o.geometry.index)return;const p=o.geometry.getAttribute('position'),a=o.geometry.index.array;const inside=i=>{point.fromBufferAttribute(p,i).applyMatrix4(o.matrixWorld);return point.toArray().every((v,k)=>v>=box.min[k]&&v<=box.max[k]);};const mask=isolatedTriangleMask(a,inside),keep=[];for(let i=0;i<a.length;i+=3)if(!mask[i/3])keep.push(a[i],a[i+1],a[i+2]);o.geometry.setIndex(keep);o.geometry.computeBoundingBox();o.geometry.computeBoundingSphere();});}
function labelTexture(renderer,serial,dark,firstLight,publicId=''){
 const c=document.createElement('canvas');if(firstLight)paintLabel(c,serial,dark,publicId);else{c.width=2048;c.height=256;const g=c.getContext('2d');if(!g)throw Error('Label unavailable');g.fillStyle='#D6BD89';g.shadowColor='rgba(0,0,0,.62)';g.shadowBlur=14;g.shadowOffsetY=4;g.font='600 174px "JetBrains Mono",monospace';g.textAlign='center';g.textBaseline='middle';g.fillText(serialLabel(serial),1024,128);}
 const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=Math.min(8,renderer.capabilities.getMaxAnisotropy());return t;
}
function addLabel(renderer,raw,value,manifest,dark){
 const profile=modelSpec(value).presentation,first=profile.labelStyle==='commemorative';
 if(value.badgeId==='clear-sky-ring')return engraveRing(renderer,raw,value);
 if(!first&&['clear-sky-crown','together-with-you'].includes(value.badgeId))return engraveHonor(renderer,raw,value);
 const a=profile.anchors.serial;if(!a){const label=new THREE.Group();label.userData.serial=value.serial;label.userData.embedded=false;raw.add(label);return label;}
 if(!validateAnchor(a))throw Error('Invalid model label anchor');
 const width=a.width*(first?0.94:0.9),height=a.height*(first?0.92:0.88);
 const material=new THREE.MeshStandardMaterial({map:labelTexture(renderer,value.serial,dark,first,value.engravingPublicId),transparent:true,metalness:.38,roughness:.54,envMapIntensity:.7,depthWrite:false,polygonOffset:true,polygonOffsetFactor:-3,polygonOffsetUnits:-2});
 const label=new THREE.Mesh(new THREE.PlaneGeometry(width,height),material),normal=new THREE.Vector3().fromArray(a.normal).normalize(),up=new THREE.Vector3().fromArray(a.up).normalize(),right=new THREE.Vector3().crossVectors(up,normal).normalize();up.crossVectors(normal,right).normalize();
 label.position.fromArray(a.position);label.quaternion.setFromRotationMatrix(new THREE.Matrix4().makeBasis(right,up,normal));label.userData.serial=value.serial;label.renderOrder=3;
 if(first){let current=value.engravingPublicId||'';label.userData.updateEngraving=next=>{const id=next.engravingPublicId||'';if(id===current)return;current=id;paintLabel(material.map.image,next.serial,dark,id);material.map.needsUpdate=true;};}
 raw.add(label);return label;
}
export function createMedalAssets(renderer,manifest){
 const entries=new Map();let disposed=false,ringStudio=null;
 function ensure(value){const key=modelKey(value),spec=modelSpec(value);if(!key||!spec)return Promise.reject(Error('Unsupported verified asset'));if(entries.has(key))return entries.get(key).promise;
  const entry={value:null,evicted:false};entries.set(key,entry);
  entry.promise=loadBadgeModel('../'+spec.file).then(gltf=>{
   const raw=gltf.scene;if(disposed||entry.evicted){destroy(raw);return null;}
   stripOldSignature(raw,spec.presentation.legacySignature);
   raw.traverse(o=>{if(!o.isMesh)return;o.castShadow=false;o.receiveShadow=false;if(spec.badgeId==='clear-sky-ring')o.userData.removedDegenerateFaces=cleanRingSurface(o.geometry);for(const m of Array.isArray(o.material)?o.material:[o.material]){if(!m)continue;
    for(const value of Object.values(m))if(value?.isTexture)value.anisotropy=Math.min(16,renderer.capabilities.getMaxAnisotropy());
    applyModelMaterialPolicy(m,spec,renderer.capabilities.maxTextureSize);
    if(spec.badgeId==='clear-sky-ring'&&renderer.isWebGLRenderer){
     ringStudio??=createRingStudio(renderer);m.envMap=ringStudio.texture;m.userData.sharedStudioEnvironment=true;m.envMapIntensity=.8;m.needsUpdate=true;
     if(m.name.startsWith('Diamond')&&m.isMeshPhysicalMaterial&&m.transmission>0){
      o.geometry.computeBoundingBox();const dimensions=o.geometry.boundingBox.getSize(new THREE.Vector3());
      // KHR volume thickness is in the mesh coordinate space, before node scale.
      m.thickness=Math.min(dimensions.x,dimensions.y,dimensions.z);m.dispersion=.45;
     }
    }
   }});entry.value=raw;return raw;
  }).catch(error=>{if(entries.get(key)===entry)entries.delete(key);throw error;});return entry.promise;
 }
 function instance(value,layout,dark,transient=false){
  const assetKey=modelKey(value),prototype=entries.get(assetKey)?.value;if(!prototype)throw Error('Asset not ready');
  const raw=prototype.clone(true),oriented=new THREE.Group();oriented.quaternion.fromArray(modelSpec(value).presentation.orientation);oriented.add(raw);
  const box=new THREE.Box3().setFromObject(oriented),size=box.getSize(new THREE.Vector3()),center=box.getCenter(new THREE.Vector3());
  const metrics=boundsMetrics(box.min.toArray(),box.max.toArray());
  // Match the circular body, not the crown or vine's outer extent. The artist's
  // titanium shell is shared by this medal family; unknown art keeps bounds fitting.
  const bodyBox=new THREE.Box3();
  oriented.traverse(mesh=>{if(mesh.isMesh&&mesh.material?.name==='01 / Satin champagne titanium')bodyBox.expandByObject(mesh,true);});
  const bodySize=bodyBox.getSize(new THREE.Vector3()),bodyDiameter=Math.max(bodySize.x,bodySize.y);
  const bodySized=Number.isFinite(bodyDiameter)&&bodyDiameter>0;
  if(bodySized)metrics.scale=1/bodyDiameter;
  // Exact vertex sphere avoids the empty diagonal corners of a box shrinking a round medal.
  let radiusSquared=0;const point=new THREE.Vector3();oriented.traverse(mesh=>{const positions=mesh.geometry?.getAttribute('position');if(!positions)return;for(let i=0;i<positions.count;i++){point.fromBufferAttribute(positions,i).applyMatrix4(mesh.matrixWorld);radiusSquared=Math.max(radiusSquared,point.distanceToSquared(center));}});
  metrics.radius=Math.sqrt(radiusSquared)*metrics.scale;
  oriented.position.sub(center);
  const label=addLabel(renderer,raw,value,manifest,dark);const normalized=new THREE.Group();normalized.add(oriented);normalized.scale.setScalar(metrics.scale);const object=new THREE.Group();object.add(normalized);
  const slot=layout.slots[value.slot],home=new THREE.Vector3().fromArray(slot.badgeBottomCenter);home.y+=size.y*metrics.scale*slot.targetVisualWidth/2;object.position.copy(home);object.scale.setScalar(slot.targetVisualWidth);object.userData.instanceKey=instanceKey(value);
  return {object,label,home,radius:metrics.radius,bodySized,presentation:modelSpec(value).presentation,heightRatio:size.y*metrics.scale,width:slot.targetVisualWidth,slot:value.slot,serial:value.serial,badgeId:value.badgeId,assetKey,transient};
 }
 function releaseInstance(item){item.object.parent?.remove(item.object);item.label.parent?.remove(item.label);item.label.userData.disposeEngraving?.();destroy(item.label);}
 function trim(keep){const wanted=new Set(keep);for(const[key,entry]of entries)if(!wanted.has(key)){entry.evicted=true;if(entry.value)destroy(entry.value);entries.delete(key);}}
 function dispose(){if(disposed)return;disposed=true;trim([]);ringStudio?.dispose();ringStudio=null;}
 function updateInstance(item,value){const signature=value.engravingPublicId||'';if(item.cacheSignature!==signature){item.cacheSignature=signature;item.cacheRevision=(item.cacheRevision||0)+1;}item.label.userData.updateEngraving?.(value);}
 return {ensure,instance,updateInstance,releaseInstance,trim,dispose,has:key=>!!entries.get(key)?.value,keys:()=>[...entries.keys()]};
}
