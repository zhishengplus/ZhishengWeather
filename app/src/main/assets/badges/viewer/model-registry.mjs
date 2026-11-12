import {validatePresentation} from './presentation-core.mjs';
import {PRESENTATIONS} from './presentation-catalog.mjs';
// Only first-light is bundled. Honor URLs are verified private cache paths supplied by native code.
export const MODELS=Object.freeze({'first-light':Object.freeze({badgeId:'first-light-016',file:'first-light.glb',rotateX:0})});
export function modelKey(value){if(value?.badgeId==='first-light-016')return 'first-light';if(!Object.hasOwn(PRESENTATIONS,value?.badgeId??'')){try{if(!/^[a-z][a-z0-9-]{0,63}$/.test(value?.badgeId||''))return null;validatePresentation(value.presentation);}catch{return null;}}return /^remote-[a-f0-9]{64}$/.test(value?.assetKey||'')?value.assetKey:null;}
export function modelSpec(value){const key=modelKey(value);if(!key)return null;const presentation=PRESENTATIONS[value.badgeId]||validatePresentation(value.presentation);if(key==='first-light')return {...MODELS[key],presentation};return {badgeId:value.badgeId,file:'models/'+key.slice(7)+'.glb',rotateX:Math.PI/2,presentation,remote:true};}
export function instanceKey(value){return `${value.badgeId}:${value.serial}`;}
export function validDisplay(value){return !!modelKey(value)&&Number.isSafeInteger(value?.serial)&&value.serial>=1&&value.serial<=999999999999;}
