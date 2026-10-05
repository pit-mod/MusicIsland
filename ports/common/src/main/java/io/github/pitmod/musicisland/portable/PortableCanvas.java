package io.github.pitmod.musicisland.portable;

import io.github.pitmod.musicisland.music.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

/** Produces an RGBA HUD surface; the loader uploads it through Minecraft's own texture API.
 * No OpenGL calls, game-state cache mutations, or assumption about an OpenGL/Vulkan backend. */
public final class PortableCanvas {
    public static final int WIDTH=210,HEIGHT=120,DENSITY=4;
    private final BufferedImage image=new BufferedImage(WIDTH*DENSITY,HEIGHT*DENSITY,BufferedImage.TYPE_INT_ARGB);
    private final MusicMarquee titleScroll=new MusicMarquee(),artistScroll=new MusicMarquee();
    private final MusicWaveform waveform=new MusicWaveform();
    private String identity="",artHash="";
    private BufferedImage artwork,previousArtwork;
    private long artChangedAt;
    private int[] colors=ArtworkAccent.columns(null,MusicWaveform.BARS*2),priorColors=colors.clone();
    public BufferedImage draw(MusicPresentation m,MediaSnapshot s,double elapsed,long now,MusicAudio audio,PortableSettings config,String status,int pressed){
        Graphics2D g=image.createGraphics();
        try{
            g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,image.getWidth(),image.getHeight());
            g.setComposite(AlphaComposite.SrcOver);g.scale(DENSITY,DENSITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            float opacity=clamp(m.visibility.getCurrentValue());if(opacity<.003f)return image;
            float w=m.width.getCurrentValue(),h=m.height.getCurrentValue(),x=(WIDTH-w)*.5f,y=12;
            float press=clamp(m.press.getCurrentValue()+m.feedback.getCurrentValue()*.45f);
            float bodyW=w*(1-.012f*press),bodyH=h*(1-.025f*press),radius=Math.min(m.radius.getCurrentValue(),bodyH*.5f);
            Shape body=new RoundRectangle2D.Float((WIDTH-bodyW)*.5f,y+(h-bodyH)*.5f,bodyW,bodyH,radius*2,radius*2);
            for(int i=7;i>0;i--){g.setColor(new Color(0,0,0,Math.round(opacity*5)));g.fill(new RoundRectangle2D.Float((WIDTH-bodyW)*.5f-i*.5f,y+2-i*.25f,bodyW+i,bodyH+i*.5f,radius*2+i,radius*2+i));}
            g.setColor(new Color(0,0,0,Math.round(opacity*255)));g.fill(body);g.clip(body);
            String track=s==null?"":s.identity();if(!track.equals(identity)){identity=track;titleScroll.reset();artistScroll.reset();}
            updateArtwork(s,now);
            float expansion=MusicPresentation.smooth((h-MusicLayout.COMPACT_HEIGHT)/(MusicLayout.HEIGHT-MusicLayout.COMPACT_HEIGHT));
            float size=m.artSize.getCurrentValue(),ax=x+m.artX.getCurrentValue(),ay=y+m.artY.getCurrentValue();
            float breath=(1-.10f*(1-clamp(m.playback.getCurrentValue()))*expansion)*(1-.025f*m.controls.play.getCurrentValue());
            float blend=previousArtwork==null||!m.controls.animate()?1:MusicPresentation.smooth((now-artChangedAt)/440000000f);
            float travel=size*.20f*m.controls.direction,slide=m.controls.skip.getCurrentValue()*1.2f;
            if(previousArtwork!=null)cover(g,previousArtwork,ax+slide+travel*blend,ay,size,breath,opacity*(1-blend));
            cover(g,artwork,ax+slide-travel*(1-blend),ay,size,breath,opacity*blend);
            if(blend>=1)previousArtwork=null;
            waveform.update(audio,m.controls.playing(s),s==null?"":s.source,now);
            float a=clamp(m.reveal.getCurrentValue());
            if(config.indicator&&s!=null){
                float bx=x+w-13-12*expansion,by=y+8+9.5f*expansion;
                for(int i=0;i<MusicWaveform.BARS;i++){
                    float bar=1.15f+waveform.level(i)*(9.5f+2.5f*expansion),stroke=.95f+.10f*expansion,step=1.72f+.25f*expansion;
                    int c1=ArtworkAccent.blend(priorColors[i*2],colors[i*2],blend),c2=ArtworkAccent.blend(priorColors[i*2+1],colors[i*2+1],blend);
                    g.setPaint(new GradientPaint(bx+i*step,by,color(c1,opacity),bx+i*step+stroke,by,color(c2,opacity)));
                    g.fill(new RoundRectangle2D.Float(bx+i*step,by-bar*.5f,stroke,bar,stroke,stroke));
                }
            }
            float p=clamp((h-MusicLayout.COMPACT_HEIGHT)/(MusicLayout.HEIGHT-MusicLayout.COMPACT_HEIGHT));
            boolean resizing=Math.abs(m.width.getVelocity())>.5f||Math.abs(m.height.getVelocity())>.5f||Math.abs(w-m.width.getTargetValue())>.05f||Math.abs(h-m.height.getTargetValue())>.05f;
            float tx=x+18+28*p+(m.controls.incoming.getCurrentValue()+m.controls.skip.getCurrentValue()*1.5f)*p,ty=y+4.7f+10.3f*p;
            float trackAlpha=clamp(m.trackReveal.getCurrentValue());
            if((config.compactTitle&&s!=null)||a>.01f)label(g,s==null?"Nothing playing":s.title.isEmpty()?"Unknown track":s.title,tx,ty,w-(18+28*p)-16-15*p,.58f+.26f*p,color(0xFFFFFF,opacity*trackAlpha*(config.compactTitle&&s!=null?1:a)),now,titleScroll,resizing);
            float artistAlpha=MusicPresentation.smooth((p-.25f)/.65f);
            if(artistAlpha>.001f)label(g,s==null?status:s.artist.isEmpty()?s.source:s.artist,tx,ty+7+2*p,w-(18+28*p)-31,.58f+.22f*p,color(0x96969C,opacity*artistAlpha*trackAlpha),now,artistScroll,resizing);
            if(a<.01f)return image;
            float alpha=opacity*a,shift=(1-a)*3+(1-trackAlpha)*2;
            float fraction=m.controls.timeline(s,elapsed,pressed==4,now),seek=clamp(m.controls.seek.getCurrentValue());
            float lineX=x+MusicLayout.SEEK_X-seek*1.2f,lineW=MusicLayout.SEEK_WIDTH+seek*2.4f,thickness=2.6f+seek*1.25f,lineY=y+MusicLayout.SEEK_Y+shift+1.3f-thickness*.5f;
            round(g,lineX,lineY,lineW,thickness,thickness*.5f,color(0x333335,alpha));
            if(s!=null&&s.duration>0&&fraction>0)round(g,lineX,lineY,lineW*fraction,thickness,thickness*.5f,color(0xFFFFFF,alpha));
            text(g,time(elapsed),x+12,y+MusicLayout.SEEK_Y+shift-3,.56f,color(0x96969C,alpha));
            String remaining=s==null||s.duration<=0?"--:--":"-"+time(Math.max(0,s.duration-elapsed));
            float textWidth=g.getFontMetrics(font(.56f)).stringWidth(remaining);
            text(g,remaining,x+w-11-textWidth,y+MusicLayout.SEEK_Y+shift-3,.56f,color(0x96969C,alpha));
            float mid=x+w*.5f,cy=y+MusicLayout.CONTROL_Y+shift;
            skip(g,mid-MusicLayout.CONTROL_SPACING,cy,false,s!=null&&s.previous,alpha,m.controls.previous.getCurrentValue(),m.controls.direction<0?m.controls.skipPhase(now):1);
            transport(g,mid,cy,m.controls.glyph.value(now),alpha,m.controls.play.getCurrentValue());
            skip(g,mid+MusicLayout.CONTROL_SPACING,cy,true,s!=null&&s.next,alpha,m.controls.next.getCurrentValue(),m.controls.direction>0?m.controls.skipPhase(now):1);
            if(!status.isEmpty())label(g,status,x+16,y+81,w-32,.43f,color(0xC0C0C4,alpha),0,null,false);
        }finally{g.dispose();}
        return image;
    }
    private void updateArtwork(MediaSnapshot s,long now){
        String hash=s==null||s.artwork==null?"":s.artHash;if(hash.equals(artHash))return;
        float blend=MusicPresentation.smooth((now-artChangedAt)/440000000f);
        for(int i=0;i<colors.length;i++)priorColors[i]=ArtworkAccent.blend(priorColors[i],colors[i],blend);
        colors=ArtworkAccent.columns(s==null?null:s.artwork,MusicWaveform.BARS*2);
        previousArtwork=artwork;artwork=s==null?null:s.artwork;artChangedAt=now;artHash=hash;
    }
    private static void cover(Graphics2D g,BufferedImage art,float x,float y,float size,float scale,float opacity){
        if(opacity<=.001f)return;Graphics2D child=(Graphics2D)g.create();
        try{
            child.translate(x+size*.5f,y+size*.5f);child.scale(scale,scale);child.translate(-size*.5f,-size*.5f);
            child.clip(new RoundRectangle2D.Float(0,0,size,size,size*.4f,size*.4f));child.setComposite(AlphaComposite.SrcOver.derive(clamp(opacity)));
            if(art==null){child.setColor(new Color(0x222226));child.fill(new Rectangle2D.Float(0,0,size,size));child.setColor(new Color(0xA0A0A8));child.setStroke(new BasicStroke(Math.max(.6f,size*.055f)));child.draw(new Line2D.Float(size*.62f,size*.22f,size*.62f,size*.68f));child.fill(new Ellipse2D.Float(size*.35f,size*.56f,size*.28f,size*.28f));}
            else{int crop=Math.min(art.getWidth(),art.getHeight()),sx=(art.getWidth()-crop)/2,sy=(art.getHeight()-crop)/2;child.drawImage(art.getSubimage(sx,sy,crop,crop),AffineTransform.getScaleInstance(size/crop,size/crop),null);}
        }finally{child.dispose();}
    }
    private static Font font(float scale){return new Font(Font.DIALOG,Font.PLAIN,36).deriveFont(9*scale);}
    private static void text(Graphics2D g,String text,float x,float y,float scale,Color color){g.setFont(font(scale));g.setColor(color);g.drawString(clean(text),x,y+g.getFontMetrics().getAscent());}
    private static void label(Graphics2D g,String value,float x,float y,float width,float scale,Color color,long now,MusicMarquee scroll,boolean resizing){
        if(width<=0||color.getAlpha()<1)return;String valueClean=clean(value);Graphics2D child=(Graphics2D)g.create();
        try{child.setFont(font(scale));float textWidth=child.getFontMetrics().stringWidth(valueClean),available=width/scale;
            float offset=scroll==null?0:scroll.update(valueClean,textWidth/scale,available,resizing,now)*scale;
            child.clip(new Rectangle2D.Float(x,y-.5f,width,10*scale));text(child,valueClean,x-offset,y,scale,color);
            if(offset>0)text(child,valueClean,x-offset+textWidth+MusicMarquee.GAP*scale,y,scale,color);
        }finally{child.dispose();}
    }
    private static String clean(String value){if(value==null||value.isEmpty())return " ";StringBuilder b=new StringBuilder();int count=0;for(int i=0;i<value.length()&&count++<256;){int cp=value.codePointAt(i);i+=Character.charCount(cp);b.appendCodePoint(Character.isISOControl(cp)?' ':cp);}return b.toString();}
    private static void skip(Graphics2D g,float x,float y,boolean next,boolean enabled,float alpha,float pulse,float phase){
        Graphics2D child=(Graphics2D)g.create();try{int direction=next?1:-1;child.translate(x+(float)Math.sin(Math.PI*phase)*1.8f*direction,y);child.scale(1-.18f*pulse,1-.12f*pulse);child.setColor(color(enabled?0xFFFFFF:0x414145,alpha));polygon(child,new float[]{-7.2f*direction,-4.3f,0,0,-7.2f*direction,4.3f});polygon(child,new float[]{0,-4.3f,7.2f*direction,0,0,4.3f});}finally{child.dispose();}
    }
    private static void transport(Graphics2D g,float x,float y,float playing,float alpha,float pulse){
        Graphics2D child=(Graphics2D)g.create();try{child.translate(x,y);child.scale(1-.07f*pulse,1-.07f*pulse);child.setColor(color(0xFFFFFF,alpha));float t=clamp(playing);
            float[] left={mix(-4,-4.7f,t),-6.1f,mix(1.3f,-1.3f,t),mix(-3.1f,-6.1f,t),mix(1.3f,-1.3f,t),mix(3.1f,6.1f,t),mix(-4,-4.7f,t),6.1f};
            float[] right={1.3f,mix(-3.1f,-6.1f,t),mix(6.1f,4.7f,t),mix(0,-6.1f,t),mix(6.1f,4.7f,t),mix(0,6.1f,t),1.3f,mix(3.1f,6.1f,t)};
            Area union=new Area(path(left));union.add(new Area(path(right)));child.fill(union);child.setStroke(new BasicStroke(mix(.64f,1,t),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));child.draw(union);
        }finally{child.dispose();}
    }
    private static Path2D.Float path(float[] points){Path2D.Float p=new Path2D.Float();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);p.closePath();return p;}
    private static void polygon(Graphics2D g,float[] points){g.fill(path(points));}
    private static void round(Graphics2D g,float x,float y,float w,float h,float r,Color color){g.setColor(color);g.fill(new RoundRectangle2D.Float(x,y,w,h,r*2,r*2));}
    private static float mix(float a,float b,float t){return a+(b-a)*t;}
    private static float clamp(float v){return Math.max(0,Math.min(1,v));}
    private static Color color(int rgb,float opacity){return new Color(((int)(clamp(opacity)*255)<<24)|(rgb&0xFFFFFF),true);}
    private static String time(double seconds){int t=(int)Math.max(0,seconds);return t>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",t/3600,t/60%60,t%60):String.format(Locale.ROOT,"%d:%02d",t/60,t%60);}
}
