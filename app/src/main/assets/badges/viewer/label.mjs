export const EDITION = '初光 · 0.1.6'; // The commemorative edition never follows the running app version.
export function serialLabel(serial) {
  if (serial == null) return 'NO.------';
  if (!Number.isSafeInteger(serial) || serial < 1 || serial > 999999999999) throw new Error('Invalid authorized serial');
  return 'NO.'+String(serial).padStart(6,'0');
}
export function validateAnchor(anchor) {
  const vector=v=>Array.isArray(v)&&v.length===3&&v.every(Number.isFinite);
  return !!anchor && vector(anchor.position) && vector(anchor.normal) && vector(anchor.up) &&
    Math.hypot(...anchor.normal)>.9 && Math.hypot(...anchor.up)>.9 &&
    Math.abs(anchor.normal.reduce((sum,x,i)=>sum+x*anchor.up[i],0))/(Math.hypot(...anchor.normal)*Math.hypot(...anchor.up))<.95 &&
    Number.isFinite(anchor.width)&&anchor.width>0&&Number.isFinite(anchor.height)&&anchor.height>0;
}
export function paintLabel(canvas,serial,dark,publicId='') {
  canvas.width=2048;canvas.height=384;
  const context=canvas.getContext('2d');if(!context)throw new Error('Canvas unavailable');
  context.clearRect(0,0,canvas.width,canvas.height);
  context.textAlign='center';context.textBaseline='middle';
  // The back plate stays black in both app themes; retain warm, legible inlay contrast.
  context.fillStyle='#D6BD89';
  // A restrained drop shadow reads as an inlay edge rather than a floating sticker.
  context.shadowColor='rgba(0,0,0,.58)';context.shadowBlur=12;context.shadowOffsetY=4;
  let size=172;context.font=`600 ${size}px "JetBrains Mono",monospace`;
  const number=serialLabel(serial);
  while(context.measureText(number).width>1820&&size>64){size-=4;context.font=`600 ${size}px "JetBrains Mono",monospace`;}
  if(publicId){
    let idSize=100;context.font=`500 ${idSize}px sans-serif`;
    while(context.measureText(publicId).width>1820&&idSize>36){idSize-=2;context.font=`500 ${idSize}px sans-serif`;}
    context.fillText(publicId,1024,70);
    context.font=`600 ${Math.min(size,142)}px "JetBrains Mono",monospace`;context.fillText(number,1024,214);
  }else context.fillText(number,1024,136);
  context.font=publicId?'500 68px sans-serif':'500 88px sans-serif';context.globalAlpha=.92;context.fillText(EDITION,1024,publicId?340:302);context.globalAlpha=1;
  return canvas;
}
