package io.github.pitmod.musicisland.music;

import io.github.pitmod.musicisland.gui.physics.PhysicsSpring;

/** Local interaction feedback; metadata and actual media commands remain authoritative. */
public final class MusicControlMotion {
    public final MusicPlayGlyph glyph=new MusicPlayGlyph();
    public final PhysicsSpring play=new PhysicsSpring(0,620,24),previous=new PhysicsSpring(0,620,24),next=new PhysicsSpring(0,620,24);
    public final PhysicsSpring seek=new PhysicsSpring(0,460,32),timeline=new PhysicsSpring(0,460,34);
    public final PhysicsSpring incoming=new PhysicsSpring(0,320,30),skip=new PhysicsSpring(0,360,26);
    private MediaSnapshot intent;
    private boolean desiredPlaying;
    private long requestedAt,deadline,last=-1,timelineLast=-1,skipAt=-1;
    private String identity="",timelineIdentity="",motion="Fluid";
    private boolean timelineReady;
    public int direction=1;

    public void request(String operation,MediaSnapshot target,long now,boolean accepted){
        if("play".equals(operation)||"pause".equals(operation)){
            kick(play);
            if(accepted&&target!=null){intent=target;requestedAt=now;desiredPlaying="play".equals(operation);deadline=now+2000000000L;glyph.setPlaying(desiredPlaying,now,motion);}
        }else if("previous".equals(operation)||"next".equals(operation)){
            direction="next".equals(operation)?1:-1;skipAt=now;kick(direction>0?next:previous);skip.setVelocity(direction*18);skip.setTarget(0);
        }else if("seek".equals(operation))kick(seek);
    }
    private static void kick(PhysicsSpring spring){spring.snapTo(1);spring.setTarget(0);}
    public boolean playing(MediaSnapshot media){return media!=null&&(intent!=null&&intent.sameTrack(media)?desiredPlaying:media.playing);}
    public boolean optimistic(){return intent!=null;}
    public boolean animate(){return !"Off".equals(motion);}
    public float skipPhase(long now){return !animate()||skipAt<0?1:Math.max(0,Math.min(1,(now-skipAt)/320000000f));}
    public void update(MediaSnapshot media,long now,String mode,boolean heldPlay,boolean heldPrevious,boolean heldNext,String error){
        motion=mode;
        if(intent!=null&&(media==null||!intent.sameTrack(media)||now>=deadline||!error.isEmpty()||(media.receivedNanos>requestedAt&&media.playing==desiredPlaying)))intent=null;
        glyph.setPlaying(playing(media),now,mode);
        if(media!=null&&!identity.equals(media.identity())){
            if(!identity.isEmpty())incoming.snapTo(-direction*7);
            identity=media.identity();
        }
        play.setTarget(heldPlay?1:0);previous.setTarget(heldPrevious?1:0);next.setTarget(heldNext?1:0);incoming.setTarget(0);skip.setTarget(0);
        float dt=last<0?1f/60:Math.max(0,Math.min(.25f,(now-last)*1e-9f));last=now;
        for(PhysicsSpring spring:new PhysicsSpring[]{play,previous,next,incoming,skip})advance(spring,dt);
    }
    public float timeline(MediaSnapshot media,double elapsed,boolean scrubbing,long now){
        float fraction=media==null||media.duration<=0?0:(float)Math.max(0,Math.min(1,elapsed/media.duration));
        String track=media==null?"":media.identity();
        if(!timelineReady||!timelineIdentity.equals(track)){timeline.snapTo(fraction);timelineIdentity=track;timelineReady=true;}
        timeline.setTarget(fraction);seek.setTarget(scrubbing?1:0);
        float dt=timelineLast<0?1f/60:Math.max(0,Math.min(.25f,(now-timelineLast)*1e-9f));timelineLast=now;
        advance(timeline,dt);
        // Seek owns its own render clock; it is not stepped again in update().
        advance(seek,dt);
        return Math.max(0,Math.min(1,timeline.getCurrentValue()));
    }
    private void advance(PhysicsSpring spring,float dt){
        if("Off".equals(motion))spring.snapTo(spring.getTargetValue());
        else{spring.setFriction("Reduced".equals(motion)?46:(spring==play||spring==previous||spring==next?24:spring==incoming?30:spring==timeline?34:32));spring.update(dt);}
    }
    public void reset(){intent=null;glyph.reset();last=timelineLast=skipAt=-1;identity=timelineIdentity="";timelineReady=false;for(PhysicsSpring spring:new PhysicsSpring[]{play,previous,next,seek,timeline,incoming,skip})spring.snapTo(0);}
}
