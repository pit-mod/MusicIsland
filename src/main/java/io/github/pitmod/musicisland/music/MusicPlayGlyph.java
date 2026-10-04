package io.github.pitmod.musicisland.music;

/** Continuous, clock-based transport morph; reversals retain position and velocity. */
public final class MusicPlayGlyph {
    private float from,target,initialVelocity;
    private long began;
    private boolean ready;
    private double frequency=20;
    public void setPlaying(boolean playing,long now,String motion){
        float next=playing?1:0;
        if(!ready||"Off".equals(motion)){from=target=next;initialVelocity=0;began=now;ready=true;return;}
        if(next!=target){float current=value(now),speed=velocity(now);from=current;initialVelocity=speed;target=next;began=now;frequency="Reduced".equals(motion)?26:20;}
    }
    public float value(long now){
        if(now<=began)return from;
        double t=seconds(now),offset=from-target,b=initialVelocity+frequency*offset;
        double position=target+(offset+b*t)*Math.exp(-frequency*t);
        if(Math.abs(position-target)<.0005&&Math.abs(velocity(now))<.01)return target;
        return (float)Math.max(0,Math.min(1,position));
    }
    private float velocity(long now){
        double t=seconds(now),offset=from-target,b=initialVelocity+frequency*offset;
        return (float)((initialVelocity-frequency*b*t)*Math.exp(-frequency*t));
    }
    private double seconds(long now){return Math.max(0,(now-began)*1e-9);}
    public void reset(){ready=false;from=target=initialVelocity=0;began=0;}
}
