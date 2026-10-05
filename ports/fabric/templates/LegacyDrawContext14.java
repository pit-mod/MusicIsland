package io.github.pitmod.musicisland.fabric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
/** Minecraft 1.14/1.15's native texture drawing and legacy GUI matrices. */
public final class LegacyDrawContext {
    public final class Matrices {
        public void push(){GL11.glPushMatrix();}public void pop(){GL11.glPopMatrix();}
        public void translate(float x,float y,float z){GL11.glTranslatef(x,y,z);}
        public void scale(float x,float y,float z){GL11.glScalef(x,y,z);}
    }
    private final Matrices matrices=new Matrices();
    public Matrices getMatrices(){return matrices;}
    public int getScaledWindowWidth(){return MinecraftClient.getInstance().getWindow().getScaledWidth();}
    public int getScaledWindowHeight(){return MinecraftClient.getInstance().getWindow().getScaledHeight();}
    public void fill(int x1,int y1,int x2,int y2,int color){DrawableHelper.fill(x1,y1,x2,y2,color);}
    public void drawCenteredTextWithShadow(TextRenderer font,String text,int x,int y,int color){font.drawWithShadow(text,x-font.getWidth(text)/2f,y,color);}
    public void drawTexture(Identifier id,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        MinecraftClient.getInstance().getTextureManager().bindTexture(id);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        matrices.push();try{matrices.translate(x,y,0);matrices.scale((float)width/regionWidth,(float)height/regionHeight,1);DrawableHelper.drawTexture(0,0,u,v,regionWidth,regionHeight,textureWidth,textureHeight);}
        finally{matrices.pop();GL11.glPopAttrib();}
    }
}
