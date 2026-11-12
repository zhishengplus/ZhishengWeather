import * as THREE from './vendor/three.module.js';
import {engravingField} from './engraving-field.mjs';
import {attachGemOptics} from './ring-gem.mjs';

// This asset's shank is a curved band around its local X axis (millimetres).
// Project onto the inward-facing wall, never a floating label or the outer gold.
export function engraveRing(renderer,raw,value){
 const group=new THREE.Group(),canvas=document.createElement('canvas');canvas.width=2048;canvas.height=512;
 const context=canvas.getContext('2d');if(!context)throw Error('Engraving unavailable');
 const texture=new THREE.DataTexture(new Uint8Array(2048*512*4),2048,512);
 texture.flipY=true;texture.generateMipmaps=true;texture.minFilter=THREE.LinearMipmapLinearFilter;texture.magFilter=THREE.LinearFilter;
 texture.anisotropy=Math.min(16,renderer.capabilities.getMaxAnisotropy());
 const owned=[],gems=[];let signature='';
 function update(next){
  const id=typeof next.engravingPublicId==='string'?next.engravingPublicId.trim().slice(0,32):'';
  const key=next.serial+'|'+id;if(key===signature)return;signature=key;
  context.fillStyle='#000';context.fillRect(0,0,2048,512);context.fillStyle='#fff';context.textAlign='center';context.textBaseline='middle';
  function line(text,y,size,font){context.font=`500 ${size}px ${font}`;const width=context.measureText(text).width;if(width>1800)context.font=`500 ${size*1800/width}px ${font}`;context.fillText(text,1024,y);}
  if(id)line('ID '+id,153,116,'system-ui,"Noto Sans CJK SC",sans-serif');
  line('NO.'+String(next.serial).padStart(6,'0'),id?365:256,128,'"JetBrains Mono",monospace');
  texture.image.data=engravingField(context.getImageData(0,0,2048,512).data,2048,512,.018,10.35,2.2);
  texture.needsUpdate=true;group.userData.serial=next.serial;group.userData.engravingPublicId=id;
 }
 raw.traverse(mesh=>{
  if(!mesh.isMesh)return;
  if(mesh.material?.name.startsWith('Diamond')){const gem=attachGemOptics(renderer,mesh);if(gem)gems.push(gem);}
  let part=mesh;while(part&&part!==raw&&!part.name.startsWith('GOLD_|_uniform_main_shank'))part=part.parent;
  if(!part||part===raw||Array.isArray(mesh.material))return;
  const material=mesh.material.clone();owned.push(material);mesh.material=material;
  material.onBeforeCompile=shader=>{
   shader.uniforms.ringEngraving={value:texture};
   const varying='varying vec3 vRingPoint; varying vec3 vRingInnerNormal; varying float vRingScale;\n';
   shader.vertexShader=varying+shader.vertexShader;
   shader.vertexShader=shader.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\nvRingPoint=position;vRingInnerNormal=normal;vRingScale=length(modelMatrix[0].xyz);');
   shader.fragmentShader='uniform sampler2D ringEngraving;\n'+varying+shader.fragmentShader;
   shader.fragmentShader=shader.fragmentShader.replace('#include <color_fragment>',`#include <color_fragment>
    float ringAngle=atan(vRingPoint.y,vRingPoint.z);
    float ringDelta=atan(sin(ringAngle-3.141592653589793),cos(ringAngle-3.141592653589793));
    vec2 ringUv=vec2(.5-ringDelta/1.15,.5-vRingPoint.x/2.2);
    float ringFacing=dot(vRingInnerNormal.yz/max(length(vRingInnerNormal.yz),1.e-6),vRingPoint.yz/max(length(vRingPoint.yz),1.e-6));
    float ringArea=step(0.,ringUv.x)*step(ringUv.x,1.)*step(0.,ringUv.y)*step(ringUv.y,1.)*step(ringFacing,-.6);
    vec3 ringField=texture2D(ringEngraving,clamp(ringUv,0.,1.)).rgb;
    float ringCut=ringField.r*ringArea;
    diffuseColor.rgb*=mix(1.,.38,ringCut);`);
   shader.fragmentShader=shader.fragmentShader.replace('#include <roughnessmap_fragment>','#include <roughnessmap_fragment>\nroughnessFactor=mix(roughnessFactor,.40,ringCut);');
   shader.fragmentShader=shader.fragmentShader.replace('#include <normal_fragment_maps>',`#include <normal_fragment_maps>
    vec3 ringDx=dFdx(-vViewPosition),ringDy=dFdy(-vViewPosition);
    vec3 ringR1=cross(ringDy,normal),ringR2=cross(normal,ringDx);
    float ringDet=dot(ringDx,ringR1);
    vec2 ringFootprint=max(abs(dFdx(ringUv)),abs(dFdy(ringUv)))*vec2(2048.,512.);
    float ringRelief=1.-smoothstep(3.,12.,max(ringFootprint.x,ringFootprint.y));
    vec2 ringSlope=(ringField.gb*255.-128.)/127.*vec2(10.35,2.2)*ringArea*ringRelief*vRingScale;
    vec3 ringGradient=sign(ringDet)*(dot(ringSlope,dFdx(ringUv))*ringR1+dot(ringSlope,dFdy(ringUv))*ringR2);
    if(abs(ringDet)>1.e-12)normal=normalize(abs(ringDet)*normal-ringGradient);`);
  };
  material.customProgramCacheKey=()=> 'ring-inner-engraving-v1-filtered-relief';
 });
 group.userData.embedded=true;group.userData.updateEngraving=update;
 group.userData.gemOptics=gems.map(g=>g.planes);
 group.userData.disposeEngraving=()=>{texture.dispose();owned.forEach(m=>m.dispose());gems.forEach(g=>g.dispose());};
 update(value);raw.add(group);return group;
}
