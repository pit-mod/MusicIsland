package io.github.pitmod.musicisland.music;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.awt.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

/** Bounded, antialiased Unicode labels. Independent of Minecraft's cached font/texture binding. */
public final class MusicTextRenderer implements AutoCloseable {
    private static final Font FONT=new Font(Font.DIALOG,Font.PLAIN,36);
    private static final FontRenderContext METRICS=new FontRenderContext(null,true,true);
    private final LinkedHashMap<String,Label> labels=new LinkedHashMap<String,Label>(64,.75f,true);
    private static final class Label {
        final float width;final int pixelWidth,pixelHeight;BufferedImage pixels;int texture;
        Label(String text){
            TextLayout layout=new TextLayout(text,FONT,METRICS);
            width=layout.getAdvance()/4f;pixelWidth=(int)Math.ceil(layout.getAdvance())+4;
            pixelHeight=(int)Math.ceil(layout.getAscent()+layout.getDescent()+layout.getLeading())+4;
            pixels=new BufferedImage(pixelWidth,pixelHeight,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=pixels.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setColor(Color.WHITE);
            layout.draw(g,2,2+layout.getAscent());g.dispose();
        }
    }
    public static String normalize(String value){
        if(value==null||value.isEmpty())return " ";StringBuilder clean=new StringBuilder();
        int count=0;for(int i=0;i<value.length()&&count<256;count++){
            int cp=value.codePointAt(i);i+=Character.charCount(cp);
            clean.appendCodePoint(Character.isISOControl(cp)?' ':cp);
        }
        String text=clean.toString();
        // Keep texture dimensions bounded even for pathological metadata or very wide glyphs.
        while(new TextLayout(text,FONT,METRICS).getAdvance()>4092&&text.length()>1)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
        return text;
    }
    private Label label(String value){
        Label result=labels.get(value);if(result!=null)return result;
        String text=normalize(value);result=labels.get(text);
        if(result==null){result=new Label(text);labels.put(text,result);if(labels.size()>64){String oldest=labels.keySet().iterator().next();Label prior=labels.remove(oldest);if(prior.texture!=0)GL11.glDeleteTextures(prior.texture);}}
        return result;
    }
    public float width(String value){return label(value).width;}
    public void draw(String value,float x,float y,float scale,int color){
        if(value==null||value.isEmpty()||(color>>>24)<4)return;Label label=label(value);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        try{
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            if(label.texture==0){
                ByteBuffer bytes=BufferUtils.createByteBuffer(label.pixelWidth*label.pixelHeight*4);
                for(int py=0;py<label.pixelHeight;py++)for(int px=0;px<label.pixelWidth;px++){int c=label.pixels.getRGB(px,py);bytes.put((byte)255).put((byte)255).put((byte)255).put((byte)(c>>>24));}bytes.flip();
                label.texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,label.texture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,label.pixelWidth,label.pixelHeight,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);label.pixels=null;
            }else GL11.glBindTexture(GL11.GL_TEXTURE_2D,label.texture);
            GL11.glColor4f(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,(color>>>24)/255f);
            float left=x-.5f*scale,top=y-.5f*scale,right=left+label.pixelWidth*.25f*scale,bottom=top+label.pixelHeight*.25f*scale;
            GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(left,top);GL11.glTexCoord2f(1,0);GL11.glVertex2f(right,top);GL11.glTexCoord2f(1,1);GL11.glVertex2f(right,bottom);GL11.glTexCoord2f(0,1);GL11.glVertex2f(left,bottom);GL11.glEnd();
        }finally{GL11.glPopAttrib();}
    }
    public void close(){for(Label label:labels.values())if(label.texture!=0)GL11.glDeleteTextures(label.texture);labels.clear();}
}
