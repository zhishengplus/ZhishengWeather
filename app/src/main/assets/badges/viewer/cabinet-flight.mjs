// Time-based reversible scene transition. No identities, clocks or GPU dependency.
export const ease = t => { t=Math.max(0,Math.min(1,t)); return t*t*t*(t*(t*6-15)+10); };
export const bezier = (a,b,c,d,t) => a.map((v,i)=>(1-t)**3*v+3*(1-t)**2*t*b[i]+3*(1-t)*t*t*c[i]+t**3*d[i]);
export function renderScale(width,height,dpr,moving,maxTexture=4096,detail=false){
 const w=Math.max(1,width),h=Math.max(1,height),native=Number.isFinite(dpr)&&dpr>0?dpr:1;
 // Touch-down/up never resizes the physical canvas or changes its pixel density.
 return Math.min(native,3.25,Math.sqrt(4200000/(w*h)),maxTexture/Math.max(w,h));
}
export class CabinetFlight {
 constructor(duration=820){this.duration=duration;this.progress=0;this.target=0;this.paused=false;this.disposed=false;}
 get phase(){return this.disposed?'disposed':this.progress===0&&this.target===0?'overview':this.progress===1&&this.target===1?'inspect':this.target===1?'entering':'returning';}
 get moving(){return !this.disposed&&!this.paused&&this.progress!==this.target;}
 enter(){if(this.disposed||this.target===1)return false;this.target=1;return true;}
 back(){if(this.disposed||this.target===0)return false;this.target=0;return true;}
 tick(ms,reduced=false){if(!this.moving)return false;const step=reduced?1:Math.max(0,Math.min(250,Number.isFinite(ms)?ms:0))/this.duration;
  this.progress=this.target?Math.min(1,this.progress+step):Math.max(0,this.progress-step);return true;}
 pause(){this.paused=true;} resume(){if(!this.disposed)this.paused=false;}
 dispose(){this.disposed=true;this.paused=true;}
}
