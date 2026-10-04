package io.github.pitmod.musicisland.music;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Synthetic, labeled test media. It has no provider or control side effects. */
public final class MusicPreview {
    private static final BufferedImage ART=art();
    public static MediaSnapshot at(double seconds,long now) {
        int phase=(int)(seconds/7)%8;
        boolean playing=phase!=2;
        String title=phase==3?"A very long title that continues beyond the island — midnight session":phase==1||phase==2?"After Hours":"Midnight Drive";
        String artist=phase==3?"MusicIsland preview ensemble · Extended artist name":"MusicIsland Preview";
        boolean supported=phase!=6;
        String track=phase==2?"track-1":phase==7?"rapid-"+(int)(seconds%7/.4):"track-"+phase;
        return new MediaSnapshot("preview",track,"PREVIEW",title,artist,"Reference preview",playing,
                phase==2?100:100+seconds%7,phase==5?0:200,1,now,0,supported,supported,supported,supported,supported,false,
                phase==4?"":"preview-cover",phase==4||seconds%7<.8?null:ART);
    }
    public static MediaSnapshot fixed(long now){return at(1,now);}
    public static final class Controller {
        private String identity="";private Boolean playing;private Double position;private long changed;
        private double offset;
        public MediaSnapshot get(double seconds,long now){MediaSnapshot base=at(Math.max(0,seconds+offset),now);
            if(!identity.equals(base.identity())){identity=base.identity();playing=null;position=null;}
            boolean active=playing==null?base.playing:playing;
            double elapsed=position==null?base.position:position+(active?Math.max(0,now-changed)*1e-9:0);
            return new MediaSnapshot(base.session,base.track,base.source,base.title,base.artist,base.album,active,Math.min(base.duration>0?base.duration:Double.MAX_VALUE,elapsed),base.duration,1,now,0,base.play,base.pause,base.previous,base.next,base.seek,false,base.artHash,base.artwork);
        }
        public void command(String operation,MediaSnapshot target,double candidate,long now){if(!target.identity().equals(identity))return;
            if("seek".equals(operation)&&target.seek){position=Math.max(0,Math.min(target.duration,candidate));changed=now;}
            else if("play".equals(operation)||"pause".equals(operation)){position=target.elapsed(now);changed=now;playing="play".equals(operation);}
            else if("next".equals(operation)&&target.next)offset+=7;
            else if("previous".equals(operation)&&target.previous)offset=Math.max(0,offset-7);
        }
        public void reset(){identity="";playing=null;position=null;offset=0;}
    }
    private static BufferedImage art(){BufferedImage b=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);Graphics2D g=b.createGraphics();
        g.setPaint(new GradientPaint(0,0,new Color(220,122,35),128,128,new Color(47,14,40)));g.fillRect(0,0,128,128);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(new Color(30,13,30));g.fillOval(37,18,67,97);g.setColor(new Color(249,174,70));g.fillOval(47,24,43,53);g.setColor(new Color(19,16,30));g.fillRoundRect(43,41,55,13,5,5);g.dispose();return b;}
}
