package io.github.pitmod.musicisland.portable;
import io.github.pitmod.musicisland.music.*;
/** Exercises the same immutable band values and frame response in every adapter. */
public final class AudioBandChecks {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void run(){
        long now=1000000000L;
        float[] values={.12f,.23f,.64f,.53f,.31f,.15f};
        MusicAudio audio=MusicAudio.bands(true,"Spotify.exe",values,now,true);
        for(int i=0;i<6;i++)require(audio.displayLevel(i+3,now)==values[i],"Native band values must reach Java without extra gain or historical shifting");
        values[2]=0;require(audio.displayLevel(5,now)==.64f,"Published bands must be immutable");
        for(int i=0;i<9;i++){
            require(audio.displayLevel(i,now+350000000L)==0,"Stale audio must become silent");
            require(audio.displayLevel(i,now-1)==0,"Future readings must stay silent");
            require(MusicAudio.bands(true,"Spotify.exe",values,now,false).displayLevel(i,now)==0,"Pause must clear every band");
            require(MusicAudio.bands(false,"Spotify.exe",values,now,true).displayLevel(i,now)==0,"Unavailable capture must clear every band");
        }
        MusicAudio invalid=MusicAudio.bands(true,"test",new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,2,0,.5f},now,true);
        require(invalid.displayLevel(3,now)==0&&invalid.displayLevel(4,now)==0&&invalid.displayLevel(5,now)==0&&invalid.displayLevel(6,now)==1,"Invalid/native out-of-range bands must be bounded");
        for(int i=0;i<9;i++)require(MusicAudio.bands(true,"test",new float[5],now,true).displayLevel(i,now)==0,"Malformed band count must stay silent");
        MusicWaveform waveform=new MusicWaveform();float[] ones={1,1,1,1,1,1},contour={.50f,.78f,1,.94f,.72f,.44f};
        for(int frame=0;frame<120;frame++){
            long stamp=now+frame*8333333L;waveform.update(MusicAudio.bands(true,"test",ones,stamp,true),true,"test",stamp);
        }
        for(int i=0;i<6;i++)require(Math.abs(waveform.level(i)-contour[i])<.000002f,"Six strokes must match the native contour");
        waveform.reset();waveform.update(MusicAudio.bands(true,"test",ones,now,true),true,"test",now);
        require(Math.abs(waveform.level(2)-(1-Math.exp(-52./60)))<.000002,"Attack must match the native Windows animation");
        waveform.update(MusicAudio.bands(true,"other",ones,now,true),true,"other",now);
        require(waveform.level(2)<.6f,"New players must discard the previous waveform");
        waveform.reset();waveform.update(audio,true,"wrong-source",now);for(int i=0;i<6;i++)require(waveform.level(i)==0,"Different players must never share bands");
        MusicAudio full=MusicAudio.SILENT,quiet=MusicAudio.SILENT;
        for(int i=0;i<300;i++){full=full.append(true,"legacy",.25f,now,true);quiet=quiet.append(true,"legacy",.025f,now,true);}
        require(full.displayLevel(8,now)-quiet.displayLevel(8,now)>.2f,"Legacy fallback must preserve volume changes instead of normalizing them away");
        require(full.level(8,now)==.25f,"Raw meter compatibility must be preserved");
        for(int i=0;i<9;i++)require(audio.append(true,"legacy",.1f,now,true).level(i,now)==(i==8?.1f:0),"Switching from spectrum to fallback must clear previous band values");
        System.out.println("PASS: "+checks+" audio band routing, immutability, stale/mute/source handling, native contour/smoothing and volume checks");
    }
    public static void main(String[] args){run();}
}
