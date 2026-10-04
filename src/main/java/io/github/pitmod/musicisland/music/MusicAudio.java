package io.github.pitmod.musicisland.music;

/** Immutable history of real per-player peak samples. This is a waveform envelope, not an FFT. */
public final class MusicAudio {
    public static final MusicAudio SILENT=new MusicAudio(false,"",0,new float[9]);
    public final boolean available;
    public final String source;
    public final long receivedNanos;
    private final float[] peaks;
    private final float[] history;
    private final int count;
    private final float mean,spread;
    public MusicAudio(boolean available,String source,long now,float[] peaks){this(available,source,now,peaks,peaks,peaks.length);}
    private MusicAudio(boolean available,String source,long now,float[] peaks,float[] history,int count){
        this.available=available;this.source=source;this.receivedNanos=now;this.peaks=clean(peaks);this.history=clean(history);this.count=count;
        float total=0;for(int i=this.history.length-count;i<this.history.length;i++)total+=this.history[i];
        mean=count==0?0:total/count;
        float[] sorted=java.util.Arrays.copyOfRange(this.history,this.history.length-count,this.history.length);java.util.Arrays.sort(sorted);
        spread=count<2?.065f:Math.max(.065f,sorted[(count-1)*9/10]-sorted[(count-1)/10]);
    }
    private static float[] clean(float[] values){float[] copy=values.clone();for(int i=0;i<copy.length;i++)copy[i]=Float.isFinite(copy[i])?Math.max(0,Math.min(1,copy[i])):0;return copy;}
    public float level(int i,long now){return available&&now>=receivedNanos&&now-receivedNanos<350000000L&&Float.isFinite(peaks[i])?Math.max(0,Math.min(1,peaks[i])):0;}
    /** Relative dynamics restore contrast in mastered music, without inventing a waveform. */
    public float displayLevel(int i,long now){
        float raw=level(i,now);if(raw<=.003f)return 0;
        float audible=Math.max(0,Math.min(1,(raw-.003f)/.057f));
        float relative=Math.max(0,Math.min(.95f,.38f+(raw-mean)/spread*.7f));
        return audible*Math.max(0,Math.min(.95f,count<8?raw:raw*.4f+relative*.6f));
    }
    public MusicAudio append(boolean available,String source,float peak,long now,boolean playing){
        float[] values=new float[9];
        if(available&&playing){if(this.source.equals(source))System.arraycopy(peaks,1,values,0,8);values[8]=Float.isFinite(peak)?Math.max(0,Math.min(1,peak)):0;}
        float[] samples=new float[32];int n=0;
        if(available&&playing){if(this.source.equals(source)){
            n=Math.min(31,count);System.arraycopy(history,history.length-n,samples,31-n,n);
        }samples[31]=values[8];n++;}
        return new MusicAudio(available,source,now,values,samples,n);
    }
}
