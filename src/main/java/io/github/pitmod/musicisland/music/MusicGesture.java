package io.github.pitmod.musicisland.music;

/** One gesture owns the pointer until the RELEASE EVENT. No raw button-state cancellation. */
public final class MusicGesture {
    public enum Owner { NONE, EXPAND, SEEK, PLAY, PREVIOUS, NEXT, OPEN }
    public Owner owner=Owner.NONE;
    private MediaSnapshot target;
    public double candidate;
    public long pressedAt;
    private MediaSnapshot pendingSeek;
    private double pendingValue;
    private long pendingDeadline;
    public boolean begin(Owner next,MediaSnapshot snapshot,long now) {
        if(owner!=Owner.NONE||snapshot==null)return false;
        owner=next;target=snapshot;candidate=snapshot.elapsed(now);pressedAt=now;return true;
    }
    public void drag(double fraction){if(owner==Owner.SEEK)candidate=Math.max(0,Math.min(1,fraction))*target.duration;}
    public boolean valid(MediaSnapshot current){return owner!=Owner.NONE&&target.sameTrack(current);}
    public MediaSnapshot target(){return target;}
    /** Zero is reserved for the plain surface; controls never compress the island. */
    public int pressedControl(){switch(owner){case PLAY:return 1;case PREVIOUS:return 2;case NEXT:return 3;case SEEK:return 4;case OPEN:return 5;default:return 0;}}
    public void reconcile(MediaSnapshot current){if(owner!=Owner.NONE&&!valid(current))cancel();if(pendingSeek!=null&&!pendingSeek.sameTrack(current))pendingSeek=null;}
    public void retainSeek(long now){if(owner==Owner.SEEK){pendingSeek=target;pendingValue=candidate;pendingDeadline=now+2000000000L;}}
    public double elapsed(MediaSnapshot current,long now,boolean commandPending,String error){
        if(owner==Owner.SEEK)return candidate;
        double actual=current==null?0:current.elapsed(now);
        if(pendingSeek!=null){if(!pendingSeek.sameTrack(current)||now>=pendingDeadline||(!commandPending&&(!error.isEmpty()||Math.abs(actual-pendingValue)<1.25)))pendingSeek=null;else return pendingValue;}
        return actual;
    }
    public void clearPending(){pendingSeek=null;}
    public void cancel(){owner=Owner.NONE;target=null;}
}
