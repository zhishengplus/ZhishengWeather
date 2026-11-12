// Filter the physical relief before it reaches the screen. Taking derivatives of
// screen-sampled letters produces different normals for each 2x2 pixel quad.
export function engravingField(rgba,width,height,depth=.00075,spanX=1.30,spanY=.254){
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<3||height<3||rgba.length!==width*height*4)throw Error('Invalid engraving field');
 const horizontal=new Float32Array(width*height),soft=new Float32Array(width*height),out=new Uint8Array(rgba.length);
 const weights=[1,4,6,4,1],at=(x,y)=>Math.max(0,Math.min(height-1,y))*width+Math.max(0,Math.min(width-1,x));
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  let v=0;for(let k=-2;k<=2;k++)v+=rgba[at(x+k,y)*4]*weights[k+2];horizontal[y*width+x]=v/(16*255);
 }
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  let v=0;for(let k=-2;k<=2;k++)v+=horizontal[at(x,y+k)]*weights[k+2];soft[y*width+x]=v/16;
 }
 const encode=v=>Math.round(128+127*Math.max(-1,Math.min(1,v)));
 for(let y=0;y<height;y++)for(let x=0;x<width;x++){
  const i=(y*width+x)*4;
  out[i]=rgba[i]; // Preserve the antialiased letter coverage independently of relief.
  out[i+1]=encode(-(soft[at(x+1,y)]-soft[at(x-1,y)])*.5*width*depth/spanX);
  // Texture flipY converts canvas-down to texture-up.
  out[i+2]=encode((soft[at(x,y+1)]-soft[at(x,y-1)])*.5*height*depth/spanY);
  out[i+3]=255;
 }
 return out;
}
