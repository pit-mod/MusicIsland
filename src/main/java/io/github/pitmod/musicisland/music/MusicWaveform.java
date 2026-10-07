package io.github.pitmod.musicisland.music;

/** Six rounded waveform strokes, driven exclusively by the selected player's measured peaks. */
public final class MusicWaveform {
    public static final int BARS=6;
    private static final float[] CONTOUR={.50f,.78f,1,.94f,.72f,.44f};
    private final float[] levels=new float[BARS];
    private long last=-1;
    private String source="";
    public void update(MusicAudio audio,boolean playing,String selectedSource,long now){
        if(!source.equals(selectedSource)){java.util.Arrays.fill(levels,0);source=selectedSource;}
        float dt=last<0?1f/60:Math.max(0,Math.min(.25f,(now-last)*1e-9f));last=now;
        boolean measured=playing&&selectedSource.equals(audio.source);
        for(int i=0;i<BARS;i++){
            // The bridge places bass in the center and mids/treble on the edges.
            float target=measured?audio.displayLevel(i+3,now)*CONTOUR[i]:0;
            levels[i]+=(target-levels[i])*(1-(float)Math.exp(-dt*(target>levels[i]?52:28)));
            if(levels[i]<.0001f)levels[i]=0;
        }
    }
    public float level(int bar){return levels[bar];}
    public void reset(){java.util.Arrays.fill(levels,0);source="";last=-1;}
}
