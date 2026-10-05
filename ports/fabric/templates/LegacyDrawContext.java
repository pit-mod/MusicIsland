package io.github.pitmod.musicisland.fabric;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
/** Native texture and text drawing for the MatrixStack GUI generation. */
public final class LegacyDrawContext {
    private final MatrixStack matrices;
    public LegacyDrawContext(MatrixStack matrices){this.matrices=matrices;}
    public MatrixStack getMatrices(){return matrices;}
    public int getScaledWindowWidth(){return MinecraftClient.getInstance().getWindow().getScaledWidth();}
    public int getScaledWindowHeight(){return MinecraftClient.getInstance().getWindow().getScaledHeight();}
    public void fill(int x1,int y1,int x2,int y2,int color){DrawableHelper.fill(matrices,x1,y1,x2,y2,color);}
    public void drawCenteredTextWithShadow(TextRenderer font,String text,int x,int y,int color){font.drawWithShadow(matrices,text,x-font.getWidth(text)/2f,y,color);}
    public void drawTexture(Identifier id,int x,int y,int width,int height,float u,float v,int regionWidth,int regionHeight,int textureWidth,int textureHeight){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);RenderSystem.setShaderTexture(0,id);
        DrawableHelper.drawTexture(matrices,x,y,width,height,u,v,regionWidth,regionHeight,textureWidth,textureHeight);
    }
}
