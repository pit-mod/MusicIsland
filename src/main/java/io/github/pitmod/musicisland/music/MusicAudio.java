package io.github.pitmod.musicisland.music;

/** Immutable real audio bands; older Windows bridges retain a peak-history fallback. */
public final class MusicAudio {
    public static final MusicAudio SILENT=new MusicAudio(false,"",0,new float[9]);
    public final boolean available;
    public final String source;
    public final long receivedNanos;
    private final float[] peaks;
    private final boolean spectrum;
    public MusicAudio(boolean available,String source,long now,float[] peaks){this(available,source,now,peaks,false);}
    private MusicAudio(boolean available,String source,long now,float[] peaks,boolean spectrum){
        this.available=available;this.source=source;this.receivedNanos=now;this.spectrum=spectrum;
        this.peaks=new float[9];for(int i=0;i<Math.min(9,peaks.length);i++)this.peaks[i]=clean(peaks[i]);
    }
    private static float clean(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    public float level(int i,long now){return available&&i>=0&&i<9&&now>=receivedNanos&&now-receivedNanos<350000000L?peaks[i]:0;}
    public float displayLevel(int i,long now){
        float raw=level(i,now);if(spectrum||raw<=0)return raw;
        // Same fixed dBFS display curve as Windows; never normalize away volume.
        float value=(float)Math.pow(Math.max(0,Math.min(1,(20*Math.log10(raw)+66)/66)),.85);
        return value+.5f*value*(1-value)*(1-value);
    }
    /** Values have already been analyzed and mapped by the native bridge. */
    public static MusicAudio bands(boolean available,String source,float[] bands,long now,boolean playing){
        float[] values=new float[9];
        if(available&&playing&&bands!=null&&bands.length==6)for(int i=0;i<6;i++)values[i+3]=clean(bands[i]);
        return new MusicAudio(available,source,now,values,true);
    }
    public MusicAudio append(boolean available,String source,float peak,long now,boolean playing){
        float[] values=new float[9];
        if(available&&playing){if(!spectrum&&this.source.equals(source))System.arraycopy(peaks,1,values,0,8);values[8]=clean(peak);}
        return new MusicAudio(available,source,now,values,false);
    }
}
