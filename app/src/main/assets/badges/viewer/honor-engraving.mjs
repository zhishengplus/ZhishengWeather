import * as THREE from './vendor/three.module.js';
import {engravingField} from './engraving-field.mjs';

// Cut the lettering into the existing back metal shading. No floating plane, cast
// shadow, new silhouette or unrelated change to the artist's metal/environment.
export function engraveHonor(renderer, raw, value) {
 const group=new THREE.Group(),canvas=document.createElement('canvas');canvas.width=2048;canvas.height=400;
 const g=canvas.getContext('2d');if(!g)throw Error('Engraving unavailable');
 const mask=new THREE.DataTexture(new Uint8Array(canvas.width*canvas.height*4),canvas.width,canvas.height);
 mask.flipY=true;mask.generateMipmaps=true;mask.minFilter=THREE.LinearMipmapLinearFilter;mask.magFilter=THREE.LinearFilter;
 mask.anisotropy=Math.min(16,renderer.capabilities.getMaxAnisotropy());
 const owned=[];let signature='';
 function update(next){
  const id=typeof next.engravingPublicId==='string'?next.engravingPublicId.trim().slice(0,32):'';
  const key=next.serial+'|'+id;if(signature===key)return;signature=key;
  g.fillStyle='#000';g.fillRect(0,0,canvas.width,canvas.height);g.fillStyle='#fff';g.textAlign='center';g.textBaseline='middle';
  function line(text,y,size,maxWidth,font){g.font=`500 ${size}px ${font}`;const width=g.measureText(text).width;if(width>maxWidth)g.font=`500 ${size*maxWidth/width}px ${font}`;g.fillText(text,1024,y);}
  if(id)line(id,140,108,1750,'system-ui,"Noto Sans CJK SC",sans-serif');
  line(String(next.serial).padStart(6,'0'),290,118,1430,'"JetBrains Mono",monospace');
  mask.image.data=engravingField(g.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height);
  mask.needsUpdate=true;group.userData.serial=next.serial;group.userData.engravingPublicId=id;
 }
 raw.updateMatrixWorld(true);const inverseRaw=raw.matrixWorld.clone().invert();
 raw.traverse(mesh=>{
  if(!mesh.isMesh||mesh.material?.name!=='01 / Satin champagne titanium')return;
  const material=mesh.material.clone(),toRaw=inverseRaw.clone().multiply(mesh.matrixWorld);owned.push(material);mesh.material=material;
  material.onBeforeCompile=shader=>{
   shader.uniforms.badgeEngraving={value:mask};shader.uniforms.badgeToRaw={value:toRaw};
   const vary='varying vec3 vBadgePoint; varying float vBadgeScale;\n';
   shader.vertexShader='uniform mat4 badgeToRaw;\n'+vary+shader.vertexShader;
   shader.vertexShader=shader.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\nvBadgePoint=(badgeToRaw*vec4(position,1.)).xyz;vBadgeScale=length(modelMatrix[0].xyz);');
   shader.fragmentShader='uniform sampler2D badgeEngraving;\n'+vary+shader.fragmentShader;
   shader.fragmentShader=shader.fragmentShader.replace('#include <color_fragment>',`#include <color_fragment>
    vec2 badgeUv=vec2(.5-vBadgePoint.x/1.30,.5-(vBadgePoint.z-1.84)/.254);
    // The rear plane is y=-.22. A threshold on that exact plane clips letters
    // unpredictably as interpolated floats move across it during rotation.
    float badgeArea=step(0.,badgeUv.x)*step(badgeUv.x,1.)*step(0.,badgeUv.y)*step(badgeUv.y,1.)*step(vBadgePoint.y,-.21);
    vec3 badgeField=texture2D(badgeEngraving,clamp(badgeUv,0.,1.)).rgb;
    float badgeCut=badgeField.r*badgeArea;
    diffuseColor.rgb*=mix(1.,.30,badgeCut);`);
   shader.fragmentShader=shader.fragmentShader.replace('#include <roughnessmap_fragment>','#include <roughnessmap_fragment>\nroughnessFactor=mix(roughnessFactor,.46,badgeCut);');
   shader.fragmentShader=shader.fragmentShader.replace('#include <normal_fragment_maps>',`#include <normal_fragment_maps>
    vec3 badgeDx=dFdx(-vViewPosition),badgeDy=dFdy(-vViewPosition);
    vec3 badgeR1=cross(badgeDy,normal),badgeR2=cross(normal,badgeDx);
    float badgeDet=dot(badgeDx,badgeR1);
    vec2 badgeFootprint=max(abs(dFdx(badgeUv)),abs(dFdy(badgeUv)))*vec2(2048.,400.);
    float badgeRelief=1.-smoothstep(3.,12.,max(badgeFootprint.x,badgeFootprint.y));
    vec2 badgeSlope=(badgeField.gb*255.-128.)/127.*vec2(1.30,.254)*badgeArea*badgeRelief*vBadgeScale;
    vec3 badgeGradient=sign(badgeDet)*(dot(badgeSlope,dFdx(badgeUv))*badgeR1+dot(badgeSlope,dFdy(badgeUv))*badgeR2);
    if(abs(badgeDet)>1.e-12)normal=normalize(abs(badgeDet)*normal-badgeGradient);`);
  };
  material.customProgramCacheKey=()=> 'honor-engraving-v2-filtered-relief';
 });
 group.userData.embedded=true;group.userData.updateEngraving=update;
 group.userData.disposeEngraving=()=>{mask.dispose();owned.forEach(m=>m.dispose());};
 update(value);raw.add(group);return group;
}
