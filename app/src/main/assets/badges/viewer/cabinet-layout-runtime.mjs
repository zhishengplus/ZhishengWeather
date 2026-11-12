import {modelKey,validDisplay} from './model-registry.mjs';
export function cabinetFrustum(width,height){
 const aspect=Math.max(1,width)/Math.max(1,height),vertical=Math.max(8,5.55/aspect);
 return {left:-vertical*aspect/2,right:vertical*aspect/2,top:vertical/2,bottom:-vertical/2};
}
/** One medal owns the stage: fit its width/height instead of reserving three cabinet slots. */
export function heroFrustum(width,height){
 const aspect=Math.max(1,width)/Math.max(1,height),vertical=Math.max(2.2,2.2/aspect);
 return {left:-vertical*aspect/2,right:vertical*aspect/2,top:vertical/2,bottom:-vertical/2};
}
export function cabinetDisplays(value){
 if(!Array.isArray(value))return [];
 const slots=new Set();return value.slice(0,3).filter(v=>v&&Number.isInteger(v.slot)&&v.slot>=0&&v.slot<3&&
  validDisplay(v)&&!slots.has(v.slot)&&slots.add(v.slot));
}
