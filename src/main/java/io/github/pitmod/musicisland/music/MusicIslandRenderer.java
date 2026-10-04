package io.github.pitmod.musicisland.music;

import io.github.pitmod.musicisland.gui.GuiDraw;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Opaque music material, independent of GUI glass. Content is masked by the same SDF as the surface. */
public final class MusicIslandRenderer implements AutoCloseable {
    private int shapeProgram,artProgram,transportProgram,texture,fbo,artTexture,previousArtTexture;
    private float previousCropX=1,previousCropY=1;
    private long artChangedAt;
    private int tw,th;private boolean core,failed;
    private String artHash="";
    private String titleIdentity="";
    private long marqueeStart;
    private final MusicMarquee titleScroll=new MusicMarquee(),artistScroll=new MusicMarquee();
    private final MusicTextRenderer textRenderer=new MusicTextRenderer();
    private final MusicWaveform waveform=new MusicWaveform();
    private int[] meterPalette=ArtworkAccent.columns(null,MusicWaveform.BARS*2);
    private int[] previousMeterPalette=meterPalette;
    private static final float BW=210,BH=120,PAD=12;
    private final IntBuffer viewport=BufferUtils.createIntBuffer(16);
    private final FloatBuffer restoredColor=BufferUtils.createFloatBuffer(16);
    public String failure="";
    public void draw(float cx,float top,float scale,MusicPresentation m,MediaSnapshot s,double elapsed,long now,boolean indicator,boolean preview,String status,int pressed) {
        draw(cx,top,scale,m,s,elapsed,now,indicator,preview,status,pressed,MusicAudio.SILENT);
    }
    public void draw(float cx,float top,float scale,MusicPresentation m,MediaSnapshot s,double elapsed,long now,boolean indicator,boolean preview,String status,int pressed,MusicAudio audio) {
        draw(cx,top,scale,m,s,elapsed,now,indicator,preview,status,pressed,audio,true);
    }
    public void draw(float cx,float top,float scale,MusicPresentation m,MediaSnapshot s,double elapsed,long now,boolean indicator,boolean preview,String status,int pressed,MusicAudio audio,boolean compactTitle) {
        if(m.visibility.getCurrentValue()<.003)return;
        if(failed||!GLContext.getCapabilities().OpenGL20){fallback(cx,top,scale,m,s);return;}
        int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        core=GLContext.getCapabilities().OpenGL30;
        if(!core&&!GLContext.getCapabilities().GL_EXT_framebuffer_object){fallback(cx,top,scale,m,s);return;}
        int draw=GL11.glGetInteger(core?GL30.GL_DRAW_FRAMEBUFFER_BINDING:EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        int read=core?GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING):draw;
        viewport.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,viewport);int vx=viewport.get(0),vy=viewport.get(1),vw=viewport.get(2),vh=viewport.get(3);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            if(shapeProgram==0){shapeProgram=program("music-island.frag");artProgram=program("music-art.frag");transportProgram=program("music-transport.frag");}
            float density=Math.max(2,Math.min(4,scale*vw/Math.max(1,io.github.pitmod.musicisland.utils.ScreenScale.get().getScaledWidth())));
            allocate((int)Math.ceil(BW*density),(int)Math.ceil(BH*density));
            upload(s,now);bind(fbo);GL11.glViewport(0,0,tw,th);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,BW,BH,0,-1000,1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL20.glUseProgram(0);
            float w=m.width.getCurrentValue(),h=m.height.getCurrentValue(),r=m.radius.getCurrentValue(),reveal=clamp(m.reveal.getCurrentValue());
            float left=(BW-w)*.5f,y=PAD;
            GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            String identity=s==null?"":s.identity();
            if(!identity.equals(titleIdentity)){titleIdentity=identity;marqueeStart=now;titleScroll.reset();artistScroll.reset();}
            content(left,y,w,h,m,s,elapsed,Math.max(0,now-marqueeStart),indicator,preview,status,pressed,reveal,now,audio,compactTitle);
            if(core){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);}else bind(draw);
            GL11.glViewport(vx,vy,vw,vh);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glPushMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL20.glUseProgram(shapeProgram);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL20.glUniform1i(uniform(shapeProgram,"content"),0);GL20.glUniform2f(uniform(shapeProgram,"bounds"),BW,BH);
            float press=clamp(m.press.getCurrentValue()+m.feedback.getCurrentValue()*.45f);GL20.glUniform2f(uniform(shapeProgram,"body"),w*(1-.012f*press),h*(1-.025f*press));
            GL20.glUniform1f(uniform(shapeProgram,"radius"),Math.min(r,h*.5f));
            GL20.glUniform2f(uniform(shapeProgram,"bubble"),m.bubbleX.getCurrentValue(),h*.5f);
            GL20.glUniform1f(uniform(shapeProgram,"bubbleRadius"),Math.max(0,m.bubbleRadius.getCurrentValue()));
            GL20.glUniform1f(uniform(shapeProgram,"neck"),Math.max(0,m.neck.getCurrentValue()));GL20.glUniform1f(uniform(shapeProgram,"opacity"),clamp(m.visibility.getCurrentValue()));
            quad(cx-BW*.5f*scale,top-PAD*scale,cx+BW*.5f*scale,top+(BH-PAD)*scale);
        } catch(RuntimeException e){failed=true;failure="Music renderer unavailable";System.err.println("[MusicIsland] Music renderer: "+e.getMessage());}
        finally{
            if(core){GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);}else bind(draw);
            GL20.glUseProgram(program);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(mode);GL11.glPopAttrib();GL13.glActiveTexture(active);
            syncMinecraftState(active);
        }
    }
    /** glPopAttrib restores the driver, but vanilla fonts/primitives also mutate Minecraft's caches. */
    private void syncMinecraftState(int active){
        GL13.glActiveTexture(GL13.GL_TEXTURE0);net.minecraft.client.renderer.GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);syncTexture();
        if(active!=GL13.GL_TEXTURE0){net.minecraft.client.renderer.GlStateManager.setActiveTexture(active);syncTexture();}
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING);
        net.minecraft.client.renderer.GlStateManager.enableBlend();net.minecraft.client.renderer.GlStateManager.disableBlend();if(blend)net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.enableAlpha();net.minecraft.client.renderer.GlStateManager.disableAlpha();if(alpha)net.minecraft.client.renderer.GlStateManager.enableAlpha();
        net.minecraft.client.renderer.GlStateManager.enableCull();net.minecraft.client.renderer.GlStateManager.disableCull();if(cull)net.minecraft.client.renderer.GlStateManager.enableCull();
        net.minecraft.client.renderer.GlStateManager.enableLighting();net.minecraft.client.renderer.GlStateManager.disableLighting();if(lighting)net.minecraft.client.renderer.GlStateManager.enableLighting();
        restoredColor.clear();GL11.glGetFloat(GL11.GL_CURRENT_COLOR,restoredColor);
        net.minecraft.client.renderer.GlStateManager.color(-1,-1,-1,-1);net.minecraft.client.renderer.GlStateManager.color(restoredColor.get(0),restoredColor.get(1),restoredColor.get(2),restoredColor.get(3));
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE,GL11.GL_ZERO);net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);
    }
    private static void syncTexture(){boolean enabled=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();net.minecraft.client.renderer.GlStateManager.disableTexture2D();if(enabled)net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);net.minecraft.client.renderer.GlStateManager.bindTexture(binding);
    }
    private void content(float x,float y,float w,float h,MusicPresentation m,MediaSnapshot s,double elapsed,long marquee,boolean indicator,boolean preview,String status,int pressed,float a,long now,MusicAudio audio,boolean compactTitle) {
        float trackAlpha=clamp(m.trackReveal.getCurrentValue());
        float art=m.artSize.getCurrentValue(),coverX=x+m.artX.getCurrentValue(),coverY=y+m.artY.getCurrentValue();
        float expandedCover=MusicPresentation.smooth((h-MusicLayout.COMPACT_HEIGHT)/(MusicLayout.HEIGHT-MusicLayout.COMPACT_HEIGHT));
        float pausedCover=1-.10f*(1-clamp(m.playback.getCurrentValue()))*expandedCover;
        float breath=pausedCover*(1-.025f*m.controls.play.getCurrentValue()),slide=m.controls.skip.getCurrentValue()*1.2f;
        GuiDraw.pushScale(coverX+art*.5f,coverY+art*.5f,breath,breath);
        float coverBlend=previousArtTexture==0||!m.controls.animate()?1:MusicPresentation.smooth((now-artChangedAt)/440000000f);
        float travel=art*.20f*m.controls.direction;
        if(previousArtTexture!=0){artTexture(previousArtTexture,previousCropX,previousCropY,coverX+slide+travel*coverBlend,coverY,art,1-coverBlend);
            if(coverBlend>=1){GL11.glDeleteTextures(previousArtTexture);previousArtTexture=0;}}
        art(coverX+slide-travel*(1-coverBlend),coverY,art,coverBlend);GuiDraw.popMatrix();
        if(m.bubbleRadius.getCurrentValue()>.5)art(BW*.5f+m.bubbleX.getCurrentValue()-4.5f,y+h*.5f-4.5f,9,1);
        waveform.update(audio,m.controls.playing(s),s==null?"":s.source,now);
        if(indicator&&(a>.1||m.bubbleRadius.getCurrentValue()<1)){
            float expansion=MusicPresentation.smooth((h-MusicLayout.COMPACT_HEIGHT)/(MusicLayout.HEIGHT-MusicLayout.COMPACT_HEIGHT));
            float by=y+8+9.5f*expansion,bx=x+w-13-12*expansion;
            float paletteBlend=m.controls.animate()?MusicPresentation.smooth((now-artChangedAt)/440000000f):1;
            for(int i=0;i<MusicWaveform.BARS;i++){
                int leftColor=ArtworkAccent.blend(previousMeterPalette[i*2],meterPalette[i*2],paletteBlend);
                int rightColor=ArtworkAccent.blend(previousMeterPalette[i*2+1],meterPalette[i*2+1],paletteBlend);
                float bar=1.15f+waveform.level(i)*(9.5f+2.5f*expansion);
                float stroke=.95f+.10f*expansion,step=1.72f+.25f*expansion;
                GuiDraw.roundedRectGradient(bx+i*step,by-bar*.5f,bx+i*step+stroke,by+bar*.5f,stroke*.5f,leftColor,rightColor);
            }
        }
        // One persistent title follows the same spring as the surface, rather than
        // fading out the compact label and introducing a separate expanded label.
        float titleProgress=clamp((h-MusicLayout.COMPACT_HEIGHT)/(MusicLayout.HEIGHT-MusicLayout.COMPACT_HEIGHT));
        boolean resizing=Math.abs(m.width.getVelocity())>.5f||Math.abs(m.height.getVelocity())>.5f
                ||Math.abs(w-m.width.getTargetValue())>.05f||Math.abs(h-m.height.getTargetValue())>.05f;
        float titleX=18+28*titleProgress,titleY=4.7f+10.3f*titleProgress,titleScale=.58f+.26f*titleProgress;
        String title=s==null?"Nothing playing":s.title.isEmpty()?"Unknown track":s.title;
        if((compactTitle&&s!=null)||a>.01f){
            GL11.glPushMatrix();GL11.glTranslatef((m.controls.incoming.getCurrentValue()+m.controls.skip.getCurrentValue()*1.5f)*titleProgress,0,0);
            clippedText(title,x+titleX,y+titleY,w-titleX-16-15*titleProgress,titleScale,color(0xFFFFFF,(compactTitle&&s!=null?1:a)*trackAlpha),now,titleScroll,resizing);
            GL11.glPopMatrix();
        }
        String artist=s==null?status:s.artist.isEmpty()?s.source:s.artist;
        float artistAlpha=MusicPresentation.smooth((titleProgress-.25f)/.65f);
        if(artistAlpha>.001f){
            GL11.glPushMatrix();GL11.glTranslatef((m.controls.incoming.getCurrentValue()+m.controls.skip.getCurrentValue()*1.5f)*titleProgress,0,0);
            clippedText(artist,x+titleX,y+titleY+7+2*titleProgress,w-titleX-31,.58f+.22f*titleProgress,color(0x96969C,artistAlpha*trackAlpha),now,artistScroll,resizing);
            GL11.glPopMatrix();
        }
        if(a<.01)return;
        int white=color(0xFFFFFF,a),gray=color(0x96969C,a);float shift=(1-a)*3+(1-trackAlpha)*2;
        if(preview)text("PREVIEW",x+12,y+81,.42f,color(0x818187,a));
        float fraction=m.controls.timeline(s,elapsed,pressed==4,now);
        float lineY=y+MusicLayout.SEEK_Y+shift,seekAmount=clamp(m.controls.seek.getCurrentValue());
        float lineX=x+MusicLayout.SEEK_X-seekAmount*1.2f,lineW=MusicLayout.SEEK_WIDTH+seekAmount*2.4f;
        float thickness=2.6f+seekAmount*1.25f,barY=lineY+1.3f-thickness*.5f;
        GuiDraw.roundedRect(lineX,barY,lineX+lineW,barY+thickness,thickness*.5f,color(0x333335,a));
        if(s!=null&&s.duration>0&&fraction>0)GuiDraw.roundedRect(lineX,barY,lineX+lineW*fraction,barY+thickness,thickness*.5f,white);
        text(time(elapsed),x+12,lineY-3f,.56f,gray);
        String total=s==null||s.duration<=0?"--:--":"-"+time(Math.max(0,s.duration-elapsed));text(total,x+w-11-textWidth(total,.56f),lineY-3f,.56f,gray);
        float mid=x+w*.5f,cy=y+MusicLayout.CONTROL_Y+shift;
        control(mid-MusicLayout.CONTROL_SPACING,cy,0,s!=null&&s.previous,a,m.controls.previous.getCurrentValue(),m.controls.direction<0?m.controls.skipPhase(now):1);
        playControl(mid,cy,a,m.controls.glyph.value(now),m.controls.play.getCurrentValue());
        control(mid+MusicLayout.CONTROL_SPACING,cy,2,s!=null&&s.next,a,m.controls.next.getCurrentValue(),m.controls.direction>0?m.controls.skipPhase(now):1);
        if(!status.isEmpty())clippedText(status,x+16,y+81,w-32,.43f,color(0xC0C0C4,a),0);
    }
    private void art(float x,float y,float size,float alpha){
        if(alpha<=.001f)return;
        if(artTexture==0){GuiDraw.roundedRect(x,y,x+size,y+size,size*.2f,color(0x222226,alpha));GuiDraw.line(x+size*.62f,y+size*.22f,x+size*.62f,y+size*.68f,Math.max(.6f,size*.055f),color(0xA0A0A8,alpha));GuiDraw.circle(x+size*.49f,y+size*.7f,size*.14f,color(0xA0A0A8,alpha));GuiDraw.line(x+size*.62f,y+size*.23f,x+size*.8f,y+size*.3f,size*.09f,color(0xA0A0A8,alpha));return;}
        artTexture(artTexture,cropX,cropY,x,y,size,alpha);
    }
    private void artTexture(int id,float cropHorizontal,float cropVertical,float x,float y,float size,float alpha){
        if(alpha<=.001f)return;
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,1,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glUseProgram(artProgram);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL20.glUniform1i(uniform(artProgram,"artwork"),0);
        GL20.glUniform2f(uniform(artProgram,"crop"),cropHorizontal,cropVertical);GL20.glUniform1f(uniform(artProgram,"alpha"),alpha);quad(x,y,x+size,y+size);GL20.glUseProgram(0);
    }
    private float cropX=1,cropY=1;
    private void upload(MediaSnapshot s,long now){String hash=s==null||s.artwork==null?"":s.artHash;if(hash.equals(artHash))return;
        float paletteBlend=MusicPresentation.smooth((now-artChangedAt)/440000000f);
        for(int i=0;i<meterPalette.length;i++)previousMeterPalette[i]=ArtworkAccent.blend(previousMeterPalette[i],meterPalette[i],paletteBlend);
        meterPalette=ArtworkAccent.columns(s==null?null:s.artwork,MusicWaveform.BARS*2);
        if(previousArtTexture!=0)GL11.glDeleteTextures(previousArtTexture);previousArtTexture=artTexture;previousCropX=cropX;previousCropY=cropY;artChangedAt=now;artTexture=0;artHash=hash;if(hash.isEmpty())return;
        BufferedImage image=s.artwork;int w=image.getWidth(),h=image.getHeight();ByteBuffer pixels=BufferUtils.createByteBuffer(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=image.getRGB(x,y);pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}pixels.flip();
        artTexture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,artTexture);parameters();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        cropX=w>h?h/(float)w:1;cropY=h>w?w/(float)h:1;
    }
    private void control(float x,float y,int icon,boolean enabled,float alpha,float pulse,float phase){
        int c=color(enabled?0xFFFFFF:0x414145,alpha);float direction=icon==2?1:-1;
        GuiDraw.pushScale(x,y,1-.18f*pulse,1-.12f*pulse);
        float travel=(float)Math.sin(Math.PI*phase)*1.8f*direction;
        GL11.glTranslatef(travel,0,0);
        triangle(x-7.2f*direction,y-4.3f,x,y,x-7.2f*direction,y+4.3f,c);
        triangle(x,y-4.3f,x+7.2f*direction,y,x,y+4.3f,c);
        if(phase>0&&phase<1){float tail=x-direction*(9-4*phase);triangle(tail-4*direction,y-3,tail,y,tail-4*direction,y+3,color(0xFFFFFF,alpha*(float)Math.sin(Math.PI*phase)*.20f));}
        GuiDraw.popMatrix();
    }
    private void playControl(float x,float y,float alpha,float playing,float pulse){
        float press=clamp(pulse);
        GuiDraw.pushScale(x,y,1-.07f*press,1-.07f*press);
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);GL11.glColor4f(1,1,1,1);
        GL20.glUseProgram(transportProgram);
        GL20.glUniform1f(uniform(transportProgram,"playing"),clamp(playing));
        GL20.glUniform1f(uniform(transportProgram,"opacity"),alpha);
        quad(x-10,y-10,x+10,y+10);
        GL20.glUseProgram(0);GL11.glEnable(GL11.GL_TEXTURE_2D);
        GuiDraw.popMatrix();
    }
    private static void triangle(float x,float y,float x2,float y2,float x3,float y3,int c){
        GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f,(c>>>24)/255f);
        roundedPolygon(new float[]{x,x2,x3},new float[]{y,y2,y3},c,.14f);
    }
    private static void roundedPolygon(float[] xs,float[] ys,int c,float rounding){
        GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f,(c>>>24)/255f);GL11.glBegin(GL11.GL_POLYGON);
        for(int i=0;i<xs.length;i++){
            int prior=(i+xs.length-1)%xs.length,next=(i+1)%xs.length;
            float ax=xs[i]+(xs[prior]-xs[i])*rounding,ay=ys[i]+(ys[prior]-ys[i])*rounding;
            float bx=xs[i]+(xs[next]-xs[i])*rounding,by=ys[i]+(ys[next]-ys[i])*rounding;
            for(int j=0;j<=6;j++){float t=j/6f,u=1-t;GL11.glVertex2f(u*u*ax+2*u*t*xs[i]+t*t*bx,u*u*ay+2*u*t*ys[i]+t*t*by);}
        }
        GL11.glEnd();GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glColor4f(1,1,1,1);
    }
    private void clippedText(String value,float x,float y,float width,float scale,int c,long now){
        clippedText(value,x,y,width,scale,c,now,null,false);
    }
    private void clippedText(String value,float x,float y,float width,float scale,int c,long now,MusicMarquee scroll,boolean resizing){
        if(value==null||value.isEmpty()||width<=0)return;
        // Scissor coordinates belong to the content FBO, not Minecraft's display.
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int)(x/BW*tw),(int)((BH-y-12)/BH*th),(int)(width/BW*tw),(int)(12/BH*th));
        float textW=textWidth(value,scale),excess=textW-width;
        float offset=0,gap=14;
        if(scroll!=null){offset=scroll.update(value,textW/scale,width/scale,resizing,now)*scale;gap=MusicMarquee.GAP*scale;}
        else if(excess>0){
            double travel=(textW+14)/7.5,phase=now*1e-9%(1.75+travel);
            offset=phase<1.75?0:(float)((phase-1.75)*7.5);
        }
        text(value,x-offset,y,scale,c);
        if(excess>0||offset>0){
            // Repeat smoothly rather than snapping the title back at an arbitrary timeout.
            if(offset>0)text(value,x-offset+textW+gap,y,scale,c);
            GuiDraw.roundedRectGradient(x+width-4,y,x+width,y+12,0,0x00000000,0xFF000000);
            if(offset>.2f)GuiDraw.roundedRectGradient(x,y,x+3,y+12,0,0xFF000000,0x00000000);
        }
        GL11.glPopAttrib();
    }

    private float textWidth(String value,float scale){return textRenderer.width(value)*scale;}
    private void text(String value,float x,float y,float scale,int c){textRenderer.draw(value,x,y,scale,c);}
    public static String time(double seconds){int t=(int)Math.max(0,seconds);return t>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",t/3600,t/60%60,t%60):String.format(Locale.ROOT,"%d:%02d",t/60,t%60);}
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
    private static int color(int rgb,float a){return ((int)(255*clamp(a))<<24)|rgb;}
    private static int uniform(int p,String s){return GL20.glGetUniformLocation(p,s);}
    private static void quad(float x,float y,float right,float bottom){GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(1,0);GL11.glVertex2f(right,y);GL11.glTexCoord2f(1,1);GL11.glVertex2f(right,bottom);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,bottom);GL11.glEnd();}
    private void allocate(int w,int h){if(w==tw&&h==th&&fbo!=0)return;if(texture!=0)GL11.glDeleteTextures(texture);if(fbo!=0){if(core)GL30.glDeleteFramebuffers(fbo);else EXTFramebufferObject.glDeleteFramebuffersEXT(fbo);}
        tw=w;th=h;texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);parameters();GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        fbo=core?GL30.glGenFramebuffers():EXTFramebufferObject.glGenFramebuffersEXT();bind(fbo);
        if(core)GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);else EXTFramebufferObject.glFramebufferTexture2DEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT,GL11.GL_TEXTURE_2D,texture,0);
        int check=core?GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER):EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT);if(check!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Music content framebuffer incomplete");
    }
    private static void parameters(){GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);}
    private void bind(int id){if(core)GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,id);else EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,id);}
    private static int program(String fragment){int vs=shader(GL20.GL_VERTEX_SHADER,"#version 120\nvarying vec2 uv;void main(){gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex;uv=gl_MultiTexCoord0.xy;}");int fs=0,p=0;
        try{fs=shader(GL20.GL_FRAGMENT_SHADER,resource(fragment));p=GL20.glCreateProgram();GL20.glAttachShader(p,vs);GL20.glAttachShader(p,fs);GL20.glLinkProgram(p);if(GL20.glGetProgrami(p,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(p,4096));return p;}catch(RuntimeException e){if(p!=0)GL20.glDeleteProgram(p);throw e;}finally{GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);}}
    private static int shader(int type,String code){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,code);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String log=GL20.glGetShaderInfoLog(s,4096);GL20.glDeleteShader(s);throw new IllegalStateException(log);}return s;}
    private static String resource(String file){try(InputStream in=MusicIslandRenderer.class.getResourceAsStream("/assets/musicisland/shaders/"+file)){if(in==null)throw new IOException(file);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException(e);}}
    private static void fallback(float x,float y,float scale,MusicPresentation m,MediaSnapshot s){float w=m.width.getCurrentValue()*scale,h=m.height.getCurrentValue()*scale;GuiDraw.roundedRect(x-w*.5f,y,x+w*.5f,y+h,m.radius.getCurrentValue()*scale,0xFF000000);if(h>60)GuiDraw.textFitScaled(s==null?"Media unavailable":s.title,x-w*.5f+12,y+14,.7f,w-24,0xFFFFFFFF,false);}
    public void close(){textRenderer.close();titleScroll.reset();artistScroll.reset();if(texture!=0)GL11.glDeleteTextures(texture);if(artTexture!=0)GL11.glDeleteTextures(artTexture);if(previousArtTexture!=0)GL11.glDeleteTextures(previousArtTexture);if(fbo!=0){if(core)GL30.glDeleteFramebuffers(fbo);else EXTFramebufferObject.glDeleteFramebuffersEXT(fbo);}if(shapeProgram!=0)GL20.glDeleteProgram(shapeProgram);if(artProgram!=0)GL20.glDeleteProgram(artProgram);if(transportProgram!=0)GL20.glDeleteProgram(transportProgram);texture=artTexture=previousArtTexture=fbo=shapeProgram=artProgram=transportProgram=0;tw=th=0;artHash="";waveform.reset();meterPalette=ArtworkAccent.columns(null,MusicWaveform.BARS*2);previousMeterPalette=meterPalette;}
}
