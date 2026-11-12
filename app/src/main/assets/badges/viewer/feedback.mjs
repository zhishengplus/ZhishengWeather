// Orientation hysteresis and dwell: free rotation never snaps and never vibrates per frame.
export class BadgeFaceFeedback {
  constructor(){this.reset();}
  reset(){this.face=null;this.candidate=null;this.since=0;this.last=-Infinity;}
  pending(){return this.candidate!==null&&this.candidate!==this.face;}
  sample(q,time){
    const facing=1-2*(q[0]*q[0]+q[1]*q[1]);
    const next=facing>.965?'front':facing<-.965?'back':null;
    if(Math.abs(facing)<.86)this.face=null;
    if(next!==this.candidate){this.candidate=next;this.since=time;}
    if(!next||next===this.face||time-this.since<100||time-this.last<650)return null;
    this.face=next;this.last=time;return next;
  }
}
