import {validatePresentation} from './presentation-core.mjs';
// Art-specific values live here, not in renderer branches. Coordinates are in original GLB space.
const firstLight={version:1,orientation:[0,0,0,1],occupancy:.74,maxZoom:3.2,labelStyle:'commemorative',anchors:{serial:{position:[0,-1.55,-.455],normal:[0,0,-1],up:[0,1,0],width:1.1,height:.22}},legacySignature:{material:'01 / Satin champagne titanium',min:[-.26,-1.58,-.495],max:[.435,-1.35,-.488]}};
const honor={legacyHonorFinish:true,version:1,orientation:[Math.SQRT1_2,0,0,Math.SQRT1_2],occupancy:.72,maxZoom:3.2,labelStyle:'serial',anchors:{serial:{position:[0,-.272,1.90],normal:[0,-1,0],up:[0,0,-1],width:.95,height:.115}}};
export const PRESENTATIONS=Object.freeze(Object.fromEntries(Object.entries({'first-light-016':firstLight,'clear-sky-crown':honor,'together-with-you':honor}).map(([k,v])=>[k,Object.freeze(validatePresentation(v))])));
