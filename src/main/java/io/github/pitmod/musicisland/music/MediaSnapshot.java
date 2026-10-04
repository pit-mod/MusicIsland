package io.github.pitmod.musicisland.music;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;

/** Immutable authoritative state. Artwork and timeline never contribute to track identity. */
public final class MediaSnapshot {
    public final String session, track, source, title, artist, album, artHash;
    public final boolean playing, play, pause, previous, next, seek, open;
    public final double position, duration, rate;
    public final long receivedNanos, timelineStamp;
    public final BufferedImage artwork;
    public final int accent;
    public MediaSnapshot(String session, String track, String source, String title, String artist, String album,
            boolean playing, double position, double duration, double rate, long receivedNanos, long timelineStamp,
            boolean play, boolean pause, boolean previous, boolean next, boolean seek, boolean open,
            String artHash, BufferedImage artwork) {
        this.session=session;this.track=track;this.source=source;this.title=title;this.artist=artist;this.album=album;
        this.playing=playing;this.position=finite(position);this.duration=finite(duration);this.rate=Double.isFinite(rate)?rate:1;
        this.receivedNanos=receivedNanos;this.timelineStamp=timelineStamp;this.play=play;this.pause=pause;
        this.previous=previous;this.next=next;this.seek=seek&&this.duration>0;this.open=open;this.artHash=artHash;this.artwork=artwork;
        this.accent=ArtworkAccent.from(artwork);
    }
    public String identity() { return session+":"+track; }
    public boolean sameTrack(MediaSnapshot other) { return other!=null&&session.equals(other.session)&&track.equals(other.track); }
    public double elapsed(long now) {
        double time=position+(playing?Math.max(0,now-receivedNanos)*1e-9*rate:0);
        return Math.max(0,duration>0?Math.min(duration,time):time);
    }
    private static double finite(double x) {return Double.isFinite(x)?Math.max(0,x):0;}
    public static MediaSnapshot decode(JsonObject j, long now, BufferedImage image) {
        return new MediaSnapshot(s(j,"session"),s(j,"track"),s(j,"source"),s(j,"title"),s(j,"artist"),s(j,"album"),
                b(j,"playing"),d(j,"position")+(b(j,"playing")?Math.max(0,Math.min(5,(System.currentTimeMillis()-j.get("stamp").getAsLong())*.001))*d(j,"rate"):0),d(j,"duration"),d(j,"rate"),now,j.get("timelineStamp").getAsLong(),
                b(j,"play"),b(j,"pause"),b(j,"previous"),b(j,"next"),b(j,"seek"),b(j,"open"),s(j,"artHash"),image);
    }
    private static String s(JsonObject j,String k){return j.has(k)&&!j.get(k).isJsonNull()?j.get(k).getAsString():"";}
    private static boolean b(JsonObject j,String k){return j.has(k)&&j.get(k).getAsBoolean();}
    private static double d(JsonObject j,String k){return j.has(k)?j.get(k).getAsDouble():0;}
}
