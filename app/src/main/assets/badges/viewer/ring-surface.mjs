// The supplied ring's boolean engraving export contains collapsed, needle-like
// triangles. Their interpolated gold normals produce bright raster streaks.
// Cull only sub-10-nanometre altitudes in this asset's millimetre coordinates;
// retain every vertex, authored normal, material and visible carved boundary.
export function cleanRingSurface(geometry){
 const position=geometry.getAttribute('position'),index=geometry.index;
 if(!position||!index)return 0;
 const kept=[];let removed=0;
 for(let i=0;i<index.count;i+=3){
  const a=index.getX(i),b=index.getX(i+1),c=index.getX(i+2);
  const ux=position.getX(b)-position.getX(a),uy=position.getY(b)-position.getY(a),uz=position.getZ(b)-position.getZ(a);
  const vx=position.getX(c)-position.getX(a),vy=position.getY(c)-position.getY(a),vz=position.getZ(c)-position.getZ(a);
  const cross=Math.hypot(uy*vz-uz*vy,uz*vx-ux*vz,ux*vy-uy*vx);
  const edge=Math.max(Math.hypot(ux,uy,uz),Math.hypot(vx,vy,vz),Math.hypot(vx-ux,vy-uy,vz-uz));
  if(cross<=edge*1.e-5||!Number.isFinite(cross)){removed++;continue;}
  kept.push(a,b,c);
 }
 if(removed)geometry.setIndex(kept);
 return removed;
}
