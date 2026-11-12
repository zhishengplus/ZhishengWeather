import * as THREE from './vendor/three.module.js';

// Derive a bounded convex optical volume from the supplied cut geometry.
// No replacement silhouette, sparkle sprites, image blur or downloaded shader.
export function gemPlanes(geometry){
 const position=geometry.getAttribute('position'),index=geometry.index;
 if(!position||position.count>4096)return null;
 geometry.computeBoundingBox();const center=geometry.boundingBox.getCenter(new THREE.Vector3());
 const size=geometry.boundingBox.getSize(new THREE.Vector3()),epsilon=Math.max(size.x,size.y,size.z)*.00002;
 const planes=[],a=new THREE.Vector3(),b=new THREE.Vector3(),c=new THREE.Vector3(),normal=new THREE.Vector3();
 const count=index?index.count:position.count;if(count%3)return null;
 for(let i=0;i<count;i+=3){
  a.fromBufferAttribute(position,index?index.getX(i):i);b.fromBufferAttribute(position,index?index.getX(i+1):i+1);c.fromBufferAttribute(position,index?index.getX(i+2):i+2);
  normal.crossVectors(b.sub(a),c.sub(a));if(normal.lengthSq()<1.e-16)continue;normal.normalize();
  let distance=normal.dot(a);if(normal.dot(center)>distance){normal.negate();distance=-distance;}
  if(planes.some(p=>normal.dot(p.normal)>.999999&&Math.abs(distance-p.distance)<epsilon))continue;
  for(let j=0;j<position.count;j++)if(normal.dot(b.fromBufferAttribute(position,j))-distance>epsilon*2)return null;
  planes.push({normal:normal.clone(),distance});if(planes.length>96)return null;
 }
 if(planes.length<4)return null;
 return {planes,epsilon};
}

export function attachGemOptics(renderer,mesh){
 if(!renderer.isWebGLRenderer||renderer.capabilities.maxTextureSize<4096||renderer.capabilities.maxFragmentUniforms<256)return null;
 const volume=gemPlanes(mesh.geometry);if(!volume)return null;
 const material=mesh.material.clone(),data=new Float32Array(volume.planes.length*4);
 volume.planes.forEach((p,i)=>data.set([...p.normal.toArray(),p.distance],i*4));
 const cameraLocal=new THREE.Vector3(),worldDirection=new THREE.Matrix3();
 let shader=null;
 // The closed-volume shader performs refraction itself, avoiding a blurry
 // whole-scene transmission buffer for this very small faceted object.
 material.transmission=0;material.dispersion=0;material.transparent=false;material.opacity=1;
 material.onBeforeCompile=program=>{
  shader=program;program.uniforms.ringGemPlanes={value:data};
  program.uniforms.ringGemCamera={value:cameraLocal};program.uniforms.ringGemWorldDirection={value:worldDirection};
  const varying='varying vec3 vRingGemPosition;varying vec3 vRingGemNormal;\n';
  program.vertexShader=varying+program.vertexShader;
  program.vertexShader=program.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\nvRingGemPosition=position;vRingGemNormal=normal;');
  program.fragmentShader=varying+program.fragmentShader;
  const helpers=`
   uniform vec4 ringGemPlanes[${volume.planes.length}];
   uniform vec3 ringGemCamera;uniform mat3 ringGemWorldDirection;
   vec3 ringGemEnvironment(vec3 direction){
    return textureCubeUV(envMap,normalize(ringGemWorldDirection*direction),.025).rgb*.8;
   }
   float ringGemFresnel(float cosine,float n1,float n2){
    float c=clamp(cosine,0.,1.);float transmitted=1.-(n1/n2)*(n1/n2)*(1.-c*c);
    if(transmitted<=0.)return 1.;float ct=sqrt(transmitted);
    float a=(n1*c-n2*ct)/max(n1*c+n2*ct,1.e-6),b=(n2*c-n1*ct)/max(n2*c+n1*ct,1.e-6);
    return .5*(a*a+b*b);
   }
   vec3 ringGemTrace(float ior){
    vec3 incoming=normalize(vRingGemPosition-ringGemCamera),entryNormal=normalize(vRingGemNormal);
    if(dot(incoming,entryNormal)>0.)entryNormal=-entryNormal;
    float entryReflect=ringGemFresnel(dot(-incoming,entryNormal),1.,ior);
    vec3 color=entryReflect*ringGemEnvironment(reflect(incoming,entryNormal));
    vec3 ray=refract(incoming,entryNormal,1./ior),origin=vRingGemPosition+ray*${volume.epsilon.toExponential(8)};
    float weight=1.-entryReflect;
    for(int bounce=0;bounce<5;bounce++){
     float nearest=1.e10;vec3 faceNormal=entryNormal;
     for(int face=0;face<${volume.planes.length};face++){
      vec4 plane=ringGemPlanes[face];float denominator=dot(ray,plane.xyz);
      if(denominator>1.e-6){float distance=(plane.w-dot(origin,plane.xyz))/denominator;
       if(distance>${(volume.epsilon*.25).toExponential(8)}&&distance<nearest){nearest=distance;faceNormal=plane.xyz;}}
     }
     if(nearest>1.e9)break;
     vec3 hit=origin+ray*nearest,exitRay=refract(ray,-faceNormal,ior);
     float reflected=ringGemFresnel(dot(ray,faceNormal),ior,1.);
     if(dot(exitRay,exitRay)>1.e-8)color+=weight*(1.-reflected)*ringGemEnvironment(exitRay);
     weight*=reflected;ray=reflect(ray,faceNormal);origin=hit+ray*${volume.epsilon.toExponential(8)};
    }
    return color+weight*ringGemEnvironment(ray);
   }
  `;
  program.fragmentShader=program.fragmentShader.replace('void main() {',helpers+'\nvoid main() {');
  program.fragmentShader=program.fragmentShader.replace('#include <opaque_fragment>',
   'outgoingLight=vec3(ringGemTrace(2.408).r,ringGemTrace(2.420).g,ringGemTrace(2.440).b);\n#include <opaque_fragment>');
 };
 material.customProgramCacheKey=()=> 'ring-cut-optics-v1-'+volume.planes.length;
 mesh.material=material;
 const oldBefore=mesh.onBeforeRender;
 mesh.onBeforeRender=function(r,s,c,g,m,...rest){
  oldBefore?.call(this,r,s,c,g,m,...rest);
  cameraLocal.setFromMatrixPosition(c.matrixWorld).applyMatrix4(this.matrixWorld.clone().invert());
  worldDirection.setFromMatrix4(this.matrixWorld);
  if(shader){shader.uniforms.ringGemCamera.value=cameraLocal;shader.uniforms.ringGemWorldDirection.value=worldDirection;}
 };
 return {material,planes:volume.planes.length,dispose(){mesh.onBeforeRender=oldBefore;material.dispose();}};
}
