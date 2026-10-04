package io.github.pitmod.musicisland.music;

/** Scroll positions live in unscaled text coordinates, never in the animated panel width. */
public final class MusicMarquee {
    public static final float GAP=18;
    private String text="";
    private float offset;
    private long last=-1,holdUntil;
    public float update(String value,float textWidth,float available,boolean resizing,long now){
        if(!value.equals(text)){reset();text=value;holdUntil=now+1750000000L;}
        double dt=last<0?0:Math.max(0,Math.min(.1,(now-last)*1e-9));last=now;
        if(resizing){holdUntil=now+550000000L;return offset;}
        if(textWidth<=available){
            offset*=Math.exp(-dt*12);if(offset<.02f)offset=0;
            holdUntil=now+1750000000L;
        }else if(now>=holdUntil){
            offset+=(float)(dt*9);
            float cycle=textWidth+GAP;
            if(offset>=cycle){offset-=cycle;holdUntil=now+1750000000L;}
        }
        return offset;
    }
    public void reset(){text="";offset=0;last=-1;holdUntil=0;}
}
