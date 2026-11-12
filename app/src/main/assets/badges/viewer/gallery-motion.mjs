import {BadgeTrackball, multiply} from './trackball.mjs';

const clamp = (x, lo, hi) => Math.max(lo, Math.min(hi, x));
// Solve x(t), then evaluate y(t): this is a timing curve, not a Bezier path.
export function cubicTiming(x, x1 = .22, y1 = .75, x2 = .25, y2 = 1) {
  x = clamp(x, 0, 1);
  if (x === 0 || x === 1) return x;
  const sample = (t, a, b) => 3 * (1-t)**2*t*a + 3*(1-t)*t*t*b + t**3;
  let lo = 0, hi = 1;
  for (let i = 0; i < 24; i++) { const t = (lo+hi)/2; if (sample(t,x1,x2) < x) lo=t; else hi=t; }
  return sample((lo+hi)/2,y1,y2);
}

/** A finite release glide. New touch, cancellation and backgrounding stop it immediately. */
export class BadgeDampedTrackball extends BadgeTrackball {
  stop() { super.stop(); this.glide = null; }
  down(...args) { this.glide = null; super.down(...args); }
  up(id, time) {
    if (!this.pointers.has(id)) return; // lostpointercapture must not restart a completed release.
    super.up(id, time);
    const speed = Math.hypot(...this.velocity);
    if (!this.pointers.size && speed > .025) {
      this.glide = {axis:this.velocity.map(v=>v/speed), angle:Math.min(Math.PI*.85,speed*.16),
        duration:.42+Math.min(speed/12,1)*.26, elapsed:0, previous:0};
    }
    this.velocity = [0,0,0];
  }
  tick(seconds) {
    if (this.pointers.size || !this.glide) return false;
    const g = this.glide;
    g.elapsed = Math.min(g.duration, g.elapsed + clamp(Number.isFinite(seconds)?seconds:0,0,.25));
    const progress = cubicTiming(g.elapsed/g.duration,.18,.85,.25,1);
    const angle = (progress-g.previous)*g.angle;
    const dq = [...g.axis.map(v=>v*Math.sin(angle/2)),Math.cos(angle/2)];
    const next = multiply(dq,this.q), length = Math.hypot(...next);
    this.q = next.map(v=>v/length); g.previous=progress;
    if (g.elapsed === g.duration) this.glide=null;
    return true;
  }
}

export function galleryFocus(width, height, heightRatio = 1, radius = .5, bodySized = false, labelHeight = 49) {
  // Fit the turning sphere AND the scaled caption in the actual native canvas.
  const reserved=Math.max(49,labelHeight)+36;
  const y=Math.min(height*.42,Math.max(0,(height-reserved)/2));
  const diameter=Math.min(width*.72,height*.58,440);
  const nominal=bodySized?diameter:diameter/Math.max(1,2*radius,heightRatio);
  const scale=Math.max(0,Math.min(nominal,width*.47/radius,(y-12)/radius,(height-y-reserved)/radius));
  return {x:width/2,y,scale,labelTop:y+radius*scale+24};
}

/** One small demonstration of depth, ending exactly at rest. Never loops. */
export function galleryInvitation(progress) {
  const t=clamp(progress,0,1);
  return t===0||t===1?0:Math.sin(t*Math.PI*2)*Math.sin(t*Math.PI)*.55;
}

/** Keep vertical touch gestures available to the collection's native scroll. */
export function galleryDragIntent(dx,dy,pointerType='touch') {
  if(Math.hypot(dx,dy)<6)return 'pending';
  return pointerType==='touch'&&Math.abs(dy)>Math.abs(dx)?'scroll':'rotate';
}
