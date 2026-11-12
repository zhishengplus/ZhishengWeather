import * as THREE from './vendor/three.module.js';

// A small neutral studio for this polished ring. Surface colours, roughness,
// metalness and IOR stay authored; only the reflected surroundings differ.
// This is shared by ring instances, with no downloaded HDR or per-frame work.
export function createRingStudio(renderer) {
 const scene=new THREE.Scene();scene.background=new THREE.Color(.18,.18,.18);
 const resources=[];
 const wall=new THREE.MeshBasicMaterial({color:new THREE.Color(.22,.22,.22),side:THREE.BackSide});
 const room=new THREE.Mesh(new THREE.BoxGeometry(10,8,10),wall);scene.add(room);resources.push(room.geometry,wall);
 function card(width,height,position,value){
  const geometry=new THREE.PlaneGeometry(width,height),material=new THREE.MeshBasicMaterial({color:new THREE.Color(value,value,value),side:THREE.DoubleSide});
  const mesh=new THREE.Mesh(geometry,material);mesh.position.fromArray(position);mesh.lookAt(0,0,0);scene.add(mesh);resources.push(geometry,material);
 }
 card(2.8,5,[-3.6,1.0,3],2.3);
 card(1.6,4,[3.8,.5,2],.95);
 card(3.0,1.8,[0,3.8,.5],1.8);
 card(2.4,3,[0,.6,-4.6],.85);
 card(7,5,[0,-3.9,0],.12);
 const generator=new THREE.PMREMGenerator(renderer);
 try{return generator.fromScene(scene,.04,.1,30);}
 finally{generator.dispose();for(const value of resources)value.dispose();}
}
