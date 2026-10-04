package io.github.pitmod.musicisland.music;

import io.github.pitmod.musicisland.gui.physics.PhysicsSpring;

/** Pure presentation model; all clocks are injected, permitting deterministic verification. */
public final class MusicPresentation {
    public enum State { HIDDEN, COMPACT_PLAYING, COMPACT_PAUSED, ANNOUNCING, EXPANDING, EXPANDED, COLLAPSING, UNAVAILABLE }
    public State state=State.HIDDEN;
    public final PhysicsSpring width=spring(82),height=spring(16),radius=spring(8),bubbleX=spring(33),bubbleRadius=spring(0),neck=spring(0),visibility=spring(0),press=spring(0);
    public final PhysicsSpring reveal=spring(0),artSize=spring(10.5f),artX=spring(4),artY=spring(2.75f);
    public final PhysicsSpring playback=spring(0),trackReveal=spring(1),feedback=spring(0);
    public final MusicControlMotion controls=new MusicControlMotion();
    public boolean explicit;
    public int announcements;
    private String identity="";
    private long changedAt=Long.MIN_VALUE,missingAt=-1,pausedAt=-1,openedAt,last=-1;
    private boolean priorPlaying;
    private static PhysicsSpring spring(float v){return new PhysicsSpring(v,300,29);}
    public void expand(long now){explicit=true;openedAt=now;}
    public void dismiss(){explicit=false;changedAt=Long.MIN_VALUE;}
    /** Surface feedback belongs to a background click, not a media command or its reply. */
    public void acknowledge(){feedback.snapTo(1);feedback.setTarget(0);}
    public void update(MediaSnapshot media,long now,double announcement,double autoCollapse,double pausedVisibility,String motion,boolean referencePreview,boolean held,boolean unavailable) {
        update(media,now,announcement,autoCollapse,pausedVisibility,motion,referencePreview,held,unavailable,0,"");
    }
    public void update(MediaSnapshot media,long now,double announcement,double autoCollapse,double pausedVisibility,String motion,boolean referencePreview,boolean held,boolean unavailable,int pressed,String error) {
        controls.update(media,now,motion,pressed==1,pressed==2,pressed==3,error);
        double dt=last<0?1.0/60:Math.max(0,(now-last)*1e-9);last=now;
        if(media!=null) {
            missingAt=-1;
            if(!media.identity().equals(identity)) {if(!identity.isEmpty())trackReveal.snapTo(0);identity=media.identity();changedAt=now;announcements++;}
            playback.setTarget(controls.playing(media)?1:0);
            if(media.playing){pausedAt=-1;}else if(pausedAt<0||priorPlaying){pausedAt=now;}
            priorPlaying=media.playing;
        } else if(missingAt<0)missingAt=now;
        if(explicit&&autoCollapse>0&&(now-openedAt)*1e-9>autoCollapse&&!held)explicit=false;
        double age=changedAt==Long.MIN_VALUE?100:(now-changedAt)*1e-9;
        double sequence=referencePreview?2.14:0.95;
        boolean announcing=media!=null&&age<sequence+announcement;
        boolean visible=media!=null&&(media.playing||explicit||held||announcing||pausedVisibility==0||(pausedAt>=0&&(now-pausedAt)*1e-9<pausedVisibility));
        if(media==null)visible=explicit||(missingAt>=0&&(now-missingAt)*1e-9<1.5&&visibility.getCurrentValue()>.01);
        // Track arrivals and transport controls animate content without opening the panel.
        boolean expanded=explicit;
        if(!visible)state=State.HIDDEN;
        else if(media==null)state=State.UNAVAILABLE;
        else if(announcing&&!explicit)state=State.ANNOUNCING;
        else if(expanded)state=height.getCurrentValue()<MusicLayout.HEIGHT-1?State.EXPANDING:State.EXPANDED;
        else if(height.getCurrentValue()>17)state=State.COLLAPSING;
        else state=media.playing?State.COMPACT_PLAYING:State.COMPACT_PAUSED;
        // This reference expands as one continuous surface, without a detached cover bubble.
        float bx=33,br=0,k=0;
        // Hide text before geometry shrinks. Reveal only after it has enough room.
        visibility.setTarget(visible?1:0);width.setTarget(expanded?MusicLayout.WIDTH:MusicLayout.COMPACT_WIDTH);height.setTarget(expanded?MusicLayout.HEIGHT:MusicLayout.COMPACT_HEIGHT);radius.setTarget(expanded?MusicLayout.RADIUS:8);
        bubbleX.setTarget(bx);bubbleRadius.setTarget(br);neck.setTarget(k);press.setTarget(held&&pressed==0?1:0);
        reveal.setTarget(expanded&&width.getCurrentValue()>145&&height.getCurrentValue()>74?1:0);
        artSize.setTarget(expanded?MusicLayout.ART_SIZE:10.5f);artX.setTarget(expanded?MusicLayout.ART_X:4);artY.setTarget(expanded?MusicLayout.ART_Y:2.75f);
        trackReveal.setTarget(1);feedback.setTarget(0);
        PhysicsSpring[] all={visibility,width,height,radius,bubbleX,bubbleRadius,neck,press,reveal,artSize,artX,artY,playback,trackReveal,feedback};
        for(PhysicsSpring s:all){if("Off".equals(motion))s.snapTo(s.getTargetValue());else{s.setTension(420);s.setFriction("Reduced".equals(motion)?44:34);s.update((float)Math.min(.25,dt));}}
    }
    public static float smooth(float v){v=Math.max(0,Math.min(1,v));return v*v*(3-2*v);}
}
