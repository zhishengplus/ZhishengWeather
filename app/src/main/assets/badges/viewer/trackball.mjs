// Independent quaternion trackball. No model, serial, identity or network data is needed here.
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
const norm = a => Math.hypot(...a);
const unit = a => { const n = norm(a); return n > 1e-10 ? a.map(x => x / n) : [0, 0, 1]; };
export const multiply = (a, b) => [
  a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],
  a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],
  a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],
  a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2],
];
const quaternion = q => { const n = norm(q); return n > 1e-10 ? q.map(x => x/n) : [0,0,0,1]; };
const axisAngle = (axis, angle) => [...axis.map(x => x*Math.sin(angle/2)), Math.cos(angle/2)];
function sphere(x, y, width, height) {
  const size = Math.max(1, Math.min(width, height));
  const a = (2*x-width)/size, b = (height-2*y)/size, r = a*a+b*b;
  return unit([a, b, r <= 1 ? Math.sqrt(1-r) : 0]);
}
function between(a, b) {
  const dot = clamp(a.reduce((sum,x,i) => sum+x*b[i],0),-1,1);
  const cross = [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
  if (dot < -.999999) {
    const other = Math.abs(a[0]) < .8 ? [1,0,0] : [0,1,0];
    return [...unit([a[1]*other[2]-a[2]*other[1],a[2]*other[0]-a[0]*other[2],a[0]*other[1]-a[1]*other[0]]),0];
  }
  return quaternion([...cross,1+dot]);
}
export class BadgeTrackball {
  constructor(maxZoom=2.5) { this.maxZoom=clamp(maxZoom,2.5,3.2);this.q=[0,0,0,1]; this.zoom=1; this.pointers=new Map(); this.velocity=[0,0,0]; this.width=1;this.height=1;this.lastMove=0; }
  pose() { return {q:[...this.q],zoom:this.zoom}; }
  setPose(value) {
    this.stop();
    this.q=Array.isArray(value?.q)&&value.q.length===4&&value.q.every(Number.isFinite)?quaternion(value.q):[0,0,0,1];
    this.zoom=Number.isFinite(value?.zoom)?clamp(value.zoom,.65,this.maxZoom):1;
  }
  resize(width,height) { this.stop();this.width=Math.max(1,width);this.height=Math.max(1,height); }
  stop() { this.velocity=[0,0,0];this.pointers.clear();this.pinch=null; }
  down(id,x,y,time) {
    this.velocity=[0,0,0];this.pointers.set(id,{x,y,time});
    if(this.pointers.size===2){const [a,b]=[...this.pointers.values()];this.pinch={distance:Math.max(1,Math.hypot(a.x-b.x,a.y-b.y)),zoom:this.zoom};}
  }
  move(id,x,y,time) {
    const before=this.pointers.get(id);if(!before)return false;
    this.pointers.set(id,{x,y,time});
    if(this.pointers.size>=2){const [a,b]=[...this.pointers.values()];if(this.pinch)this.zoom=clamp(this.pinch.zoom*Math.hypot(a.x-b.x,a.y-b.y)/this.pinch.distance,.65,this.maxZoom);this.velocity=[0,0,0];return true;}
    const dq=between(sphere(before.x,before.y,this.width,this.height),sphere(x,y,this.width,this.height));
    this.q=quaternion(multiply(dq,this.q));
    const angle=2*Math.acos(clamp(dq[3],-1,1)),axis=unit(dq.slice(0,3));
    const dt=clamp((time-before.time)/1000,.004,.1),speed=Math.min(12,angle/dt);
    this.velocity=axis.map((v,i)=>.25*this.velocity[i]+.75*v*speed);this.lastMove=time;
    return true;
  }
  up(id,time) {
    this.pointers.delete(id);
    if(this.pointers.size){this.velocity=[0,0,0];this.pinch=null;for(const p of this.pointers.values())p.time=time;}
    else if(time-this.lastMove>120)this.velocity=[0,0,0];
  }
  tick(seconds) {
    if(this.pointers.size)return false;
    const speed=norm(this.velocity);if(speed<.015){this.velocity=[0,0,0];return false;}
    // Analytic exponential integration, independent of frame rate (also at 5-10fps).
    const dt=clamp(Number.isFinite(seconds)?seconds:0,0,.25),lambda=4.5,decay=Math.exp(-lambda*dt);
    const angle=speed*(1-decay)/lambda;
    this.q=quaternion(multiply(axisAngle(this.velocity.map(x=>x/speed),angle),this.q));
    this.velocity=this.velocity.map(x=>x*decay);return true;
  }
  rotate(axis,angle) { this.stop();this.q=quaternion(multiply(axisAngle(unit(axis),angle),this.q)); }
  zoomBy(factor) { this.velocity=[0,0,0];if(Number.isFinite(factor)&&factor>0)this.zoom=clamp(this.zoom*factor,.65,this.maxZoom); }
  reset() { this.stop();this.q=[0,0,0,1];this.zoom=1; }
}
