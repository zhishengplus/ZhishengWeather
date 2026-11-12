// Pure model presentation contract. Contains no ownership, badge IDs or network access.
const finite=v=>typeof v==='number'&&Number.isFinite(v);
const vector=(v,n)=>Array.isArray(v)&&v.length===n&&v.every(finite);
export function validatePresentation(p){
 if(!p||p.version!==1||!vector(p.orientation,4)||Math.abs(Math.hypot(...p.orientation)-1)>1e-5)throw Error('INVALID_PRESENTATION');
 if(!finite(p.occupancy)||p.occupancy<.35||p.occupancy>.88||!finite(p.maxZoom)||p.maxZoom<1||p.maxZoom>4)throw Error('INVALID_PRESENTATION');
 for(const a of Object.values(p.anchors||{})){
  if(!vector(a.position,3)||!vector(a.normal,3)||!vector(a.up,3)||!finite(a.width)||!finite(a.height)||a.width<=0||a.height<=0)throw Error('INVALID_ANCHOR');
  const cross=[a.normal[1]*a.up[2]-a.normal[2]*a.up[1],a.normal[2]*a.up[0]-a.normal[0]*a.up[2],a.normal[0]*a.up[1]-a.normal[1]*a.up[0]];
  if(Math.hypot(...cross)<.01)throw Error('INVALID_ANCHOR_BASIS');
 }
 return p;
}
export function boundsMetrics(min,max){
 if(!vector(min,3)||!vector(max,3))throw Error('INVALID_BOUNDS');
 const size=max.map((v,i)=>v-min[i]);if(size.some(v=>v<0)||Math.max(...size)<1e-8)throw Error('EMPTY_BOUNDS');
 const scale=1/Math.max(...size),center=min.map((v,i)=>(v+max[i])/2),radius=Math.hypot(...size)*scale/2;
 return {size,center,scale,radius,heightRatio:size[1]/Math.max(size[0],1e-8)};
}
/** A bounding sphere fits every free-rotation angle, including thickness and crown/vine extensions. */
export function fitStage(radius,width,height,occupancy=.74){
 if(!finite(radius)||radius<=0||!finite(width)||!finite(height)||width<=0||height<=0)throw Error('INVALID_VIEWPORT');
 const aspect=width/height,vertical=2*radius/occupancy/Math.min(1,aspect);
 return {left:-vertical*aspect/2,right:vertical*aspect/2,top:vertical/2,bottom:-vertical/2,near:.01,far:Math.max(100,radius*20)};
}
/** Remove only complete connected indexed components. Never punch a rectangular hole in a shell. */
export function isolatedTriangleMask(indices,inside){
 if(indices.length%3)throw Error('INVALID_TRIANGLES');
 const parent=new Map(),find=x=>{if(!parent.has(x))parent.set(x,x);let r=x;while(parent.get(r)!==r)r=parent.get(r);while(parent.get(x)!==x){const n=parent.get(x);parent.set(x,r);x=n;}return r;},union=(a,b)=>{a=find(a);b=find(b);if(a!==b)parent.set(a,b);};
 for(let i=0;i<indices.length;i+=3){union(indices[i],indices[i+1]);union(indices[i],indices[i+2]);}
 const groups=new Map();for(const v of indices){const key=find(v);groups.set(key,(groups.get(key)??true)&&inside(v));}
 return Array.from({length:indices.length/3},(_,i)=>groups.get(find(indices[i*3]))===true);
}
