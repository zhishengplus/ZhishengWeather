// A native preparation request needs an acknowledgement. Pausing cancels native
// jobs, so an unanswered request must not suppress the next foreground visit.
export class BadgePreparationWatchdog {
  constructor(onExpired, timeoutMillis = 8_000) {
    this.onExpired = onExpired; this.timeoutMillis = timeoutMillis;
    this.asked = new Set(); this.pending = new Map(); this.failed = new Set(); this.states = new Map(); this.attempts = new Map();
  }
  request(id) {
    if (this.asked.has(id)) return false;
    this.asked.add(id);
    if (this.states.get(id) === 'loading') return false;
    const entry = {state: this.states.get(id), attempt: this.attempts.get(id)};
    entry.timer = setTimeout(() => {
      if (this.pending.get(id) !== entry) return;
      this.pending.delete(id); this.failed.add(id); this.onExpired(id);
    }, this.timeoutMillis);
    this.pending.set(id, entry); return true;
  }
  observe(id, state, attempt = 0) {
    const previous = this.states.get(id), previousAttempt = this.attempts.get(id); this.states.set(id, state);
    this.attempts.set(id, attempt);
    const pending = this.pending.get(id);
    // A retry can finish from cache before Compose emits its intermediate loading
    // state. Only the new attempt acknowledges it; the old ready frame cannot.
    if (['loading', 'ready', 'error'].includes(state) &&
        (pending ? pending.state !== state || pending.attempt !== attempt :
          !this.failed.has(id) || previous !== state || previousAttempt !== attempt)) {
      this.cancelTimer(id); this.failed.delete(id);
    } else if (!['loading', 'ready', 'error'].includes(state) && ['loading', 'ready', 'error'].includes(previous)) this.retry(id);
  }
  cancelTimer(id) { const entry = this.pending.get(id); if (entry) clearTimeout(entry.timer); this.pending.delete(id); }
  retry(id) { this.cancelTimer(id); this.asked.delete(id); this.failed.delete(id); }
  retain(ids) {
    for (const id of new Set([...this.asked, ...this.states.keys()])) if (!ids.has(id)) {
      this.retry(id); this.states.delete(id); this.attempts.delete(id);
    }
  }
  clear() {
    for (const id of this.pending.keys()) this.cancelTimer(id);
    this.asked.clear(); this.failed.clear(); this.states.clear(); this.attempts.clear();
  }
}
