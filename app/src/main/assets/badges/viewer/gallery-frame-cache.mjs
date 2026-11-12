import * as THREE from './vendor/three.module.js';

/** Native-resolution GPU copy of stationary neighbours. No readback or resampling;
 * the dragged medal keeps its original geometry, lights and live PBR materials. */
export class GalleryFrameCache {
  constructor(renderer, scene, camera, items) {
    this.renderer=renderer;this.scene=scene;this.camera=camera;this.items=items;
    this.texture=null;this.key='';this.captures=0;this.hits=0;this.disabled=false;
    this.background=new THREE.Scene();this.backgroundCamera=new THREE.Camera();
    this.material=new THREE.RawShaderMaterial({
      uniforms:{frame:{value:null}},depthTest:false,depthWrite:false,blending:THREE.NoBlending,
      vertexShader:'precision highp float;attribute vec3 position;attribute vec2 uv;varying vec2 texcoord;void main(){texcoord=uv;gl_Position=vec4(position,1.0);}',
      fragmentShader:'precision highp float;uniform sampler2D frame;varying vec2 texcoord;void main(){gl_FragColor=texture2D(frame,texcoord);}',
    });
    this.geometry=new THREE.PlaneGeometry(2,2);this.quad=new THREE.Mesh(this.geometry,this.material);this.quad.frustumCulled=false;this.background.add(this.quad);
  }
  clear(){this.texture?.dispose();this.texture=null;this.material.uniforms.frame.value=null;this.key='';}
  render(active){
    const r=this.renderer,w=r.domElement.width,h=r.domElement.height;
    if(this.disabled){r.render(this.scene,this.camera);return;}
    const moving=(Array.isArray(active)?active:[active]).filter(item=>item?.object.visible);
    const neighbours=[...this.items.values()].filter(item=>!moving.includes(item)&&item.object.visible);
    if(!moving.length||!neighbours.length){r.render(this.scene,this.camera);return;}
    const key=JSON.stringify([w,h,moving.map(item=>item.object.userData.instanceKey).sort(),...neighbours.map(item=>[
      item.object.userData.instanceKey,item.assetKey,item.cacheRevision||0,item.object.position.toArray(),item.object.quaternion.toArray(),item.object.scale.toArray()])]);
    const autoClear=r.autoClear;let fallback=false;
    try{
      if(!this.texture||this.texture.image.width!==w||this.texture.image.height!==h){
        this.clear();this.texture=new THREE.FramebufferTexture(w,h);this.material.uniforms.frame.value=this.texture;
      }
      if(key!==this.key){
        for(const item of moving)item.object.visible=false;
        try{
          r.render(this.scene,this.camera);r.copyFramebufferToTexture(this.texture);
          const gl=r.getContext?.();if(gl&&gl.getError()!==gl.NO_ERROR)throw Error('GPU frame copy failed');
        }finally{for(const item of moving)item.object.visible=true;}
        this.key=key;this.captures++;
      }else{r.render(this.background,this.backgroundCamera);this.hits++;}
      for(const item of neighbours)item.object.visible=false;
      r.autoClear=false;r.render(this.scene,this.camera);
    }catch{
      this.clear();this.disabled=true;fallback=true;
    }finally{r.autoClear=autoClear;for(const item of neighbours)item.object.visible=true;}
    if(fallback)r.render(this.scene,this.camera);
  }
  dispose(){this.clear();this.geometry.dispose();this.material.dispose();}
}
