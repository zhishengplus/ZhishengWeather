// Rejected experiment, archived 2026-09-07. Never write this artwork into app resources.
// Named lighting ramps are shared by all cloudy weather variants.
const path = require('node:path');
const sharp = require(process.env.VISTA_SHARP_MODULE || 'sharp');
const width = 640, height = 420;
const clamp = x => Math.max(0, Math.min(1, x));
const smooth = x => { x = clamp(x); return x*x*(3-2*x); };
const mix = (a,b,t) => a+(b-a)*t;
function hash(x,y,z) {
  let h = Math.imul(x,374761393)^Math.imul(y,668265263)^Math.imul(z,2147483647);
  h = Math.imul(h^(h>>>13),1274126177);
  return ((h^(h>>>16))>>>0)/4294967295;
}
function noise(x,y,z) {
  const ix=Math.floor(x), iy=Math.floor(y), iz=Math.floor(z);
  const fx=smooth(x-ix), fy=smooth(y-iy), fz=smooth(z-iz);
  const plane = dz => mix(mix(hash(ix,iy,iz+dz),hash(ix+1,iy,iz+dz),fx),
    mix(hash(ix,iy+1,iz+dz),hash(ix+1,iy+1,iz+dz),fx),fy);
  return mix(plane(0),plane(1),fz);
}
function fbm(x,y,z) {
  let sum=0, amplitude=.54;
  for(let i=0;i<4;i++) {
    sum+=amplitude*noise(x,y,z);
    x=x*2.03+13.2; y=y*2.03-6.4; z=z*2.03+9.1;
    amplitude*=.48;
  }
  return sum;
}
const lobes = [
  [3.32,1.13,.00,1.10,.76,.70], [2.72,.65,.12,.57,.50,.58],
  [3.38,.56,.04,.65,.53,.63], [3.95,.95,.04,.80,.67,.64],
  [2.27,1.19,.03,.63,.44,.50], [2.78,1.57,.04,.85,.38,.53],
];
function density(x,y,z) {
  let body=-10;
  for(const [cx,cy,cz,rx,ry,rz] of lobes) {
    const dx=(x-cx)/rx,dy=(y-cy)/ry,dz=(z-cz)/rz;
    const next=1-Math.sqrt(dx*dx+dy*dy+dz*dz);
    const blend=clamp(.5+.5*(body-next)/.32);
    body=mix(next,body,blend)+.32*blend*(1-blend);
  }
  if(body<-.22) return 0;
  return Math.max(0,body+.52*(fbm(x*3.7,y*3.7,z*3.7)-.50));
}
const ramps = {
  day: { shade:[96,124,143], light:[236,245,248] },
  night: { shade:[26,42,54], light:[112,137,151] },
};
async function main() {
  const layers=Object.fromEntries(Object.keys(ramps).map(k=>[k,Buffer.alloc(width*height*4)]));
  for(let py=0;py<height;py++) for(let px=0;px<width;px++) {
    const u=px/(width-1),v=py/(height-1);
    const mask=smooth((u-.20)/.30)*smooth(v/.18)*(1-smooth((v-.77)/.23));
    if(mask===0) continue;
    let transmission=1, illumination=0;
    for(let step=0;step<28;step++) {
      const z=1.25-step*.09;
      const d=density(u*4,v*2.8,z);
      if(d<=0) continue;
      const lit=density(u*4-.12,v*2.8-.20,z+.16);
      const light=clamp(.48+(d-lit)*3.6);
      const alpha=1-Math.exp(-d*.7);
      illumination+=transmission*alpha*light;
      transmission*=1-alpha;
      if(transmission<.01) break;
    }
    const alpha=1-transmission;
    if(alpha<.002) continue;
    const light=illumination/alpha;
    const offset=(py*width+px)*4;
    for(const [mode,ramp] of Object.entries(ramps)) {
      const data=layers[mode];
      for(let c=0;c<3;c++) data[offset+c]=Math.round(mix(ramp.shade[c],ramp.light[c],light));
      data[offset+3]=Math.round(255*alpha*mask);
    }
  }
  for(const [mode,data] of Object.entries(layers)) {
    const output=path.resolve(__dirname,`../artwork/rejected-cloud-20260907/vista_cloud_bank_${mode}.png`);
    await sharp(data,{raw:{width,height,channels:4}}).png().toFile(output);
    console.log(`Rendered original cloud bank: ${mode}`);
  }
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
