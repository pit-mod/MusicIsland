package io.github.pitmod.musicisland.forge;

import io.github.pitmod.musicisland.portable.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import java.awt.image.BufferedImage;

/** Forge 1.13 adapter. No fixed-function rendering is used by later ports. */
@Mod("musicisland")
public final class MusicIsland {
    public MusicIsland(){DistExecutor.runWhenOn(Dist.CLIENT,()->()->FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup));}
    private void setup(FMLClientSetupEvent event){new Client().initialize();}
    public static final class Client {
        private final Minecraft mc=Minecraft.getInstance();
        private final PortableEngine engine=new PortableEngine(PortableSettings.load(mc.gameDir.toPath().resolve("config/musicisland.json")));
        private final ResourceLocation id=new ResourceLocation("musicisland","hud");
        private NativeImage pixels; private DynamicTexture texture; private long lastUpload; private GuiScreen lastScreen;
        private final KeyBinding controls=key("controls",77),settings=key("settings",297),down=key("expand",264),up=key("play_pause",265),left=key("previous",263),right=key("next",262);
        private static KeyBinding key(String name,int code){KeyBinding key=new KeyBinding("key.musicisland."+name,code,"category.musicisland");ClientRegistry.registerKeyBinding(key);return key;}
        void initialize(){
            MinecraftForge.EVENT_BUS.register(this);
            ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,()->(client,parent)->new Settings(parent));
            Runtime.getRuntime().addShutdownHook(new Thread(engine::close,"MusicIsland shutdown"));
        }
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
            if(e.phase!=TickEvent.Phase.END)return;
            engine.tick(mc.world!=null&&mc.player!=null);
            if(mc.currentScreen!=lastScreen){engine.cancelGesture();lastScreen=mc.currentScreen;}
            if(!engine.active()&&texture!=null){mc.getTextureManager().deleteTexture(id);texture=null;pixels=null;lastUpload=0;}
            while(settings.isPressed())if(mc.currentScreen==null)showSettings();
            while(controls.isPressed())if(mc.currentScreen==null)showControls();
            while(down.isPressed())if(mc.currentScreen==null&&engine.config.arrows)showControls();
            while(up.isPressed())if(mc.currentScreen==null&&engine.config.arrows)engine.action("toggle");
            while(left.isPressed())if(mc.currentScreen==null&&engine.config.arrows)engine.action("previous");
            while(right.isPressed())if(mc.currentScreen==null&&engine.config.arrows)engine.action("next");
        }
        @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e){if(e.getType()==RenderGameOverlayEvent.ElementType.ALL&&mc.currentScreen==null)draw(false);}
        @SubscribeEvent public void menu(GuiScreenEvent.DrawScreenEvent.Post e){if(!(e.getGui() instanceof Island)&&!(e.getGui() instanceof Settings))draw(true);}
        @SubscribeEvent public void click(GuiScreenEvent.MouseClickedEvent.Pre e){if(engine.press(e.getMouseX(),e.getMouseY(),e.getButton()))e.setCanceled(true);}
        @SubscribeEvent public void release(GuiScreenEvent.MouseReleasedEvent.Pre e){if(engine.ownsPointer()){engine.release(e.getMouseX(),e.getMouseY(),e.getButton());e.setCanceled(true);}}
        @SubscribeEvent public void drag(GuiScreenEvent.MouseDragEvent.Pre e){if(engine.ownsPointer()){engine.drag(e.getMouseX(),e.getMouseY());e.setCanceled(true);}}
        @SubscribeEvent public void chat(ClientChatEvent e){if(e.getMessage().equalsIgnoreCase("/musicisland")){e.setCanceled(true);showSettings();}else if(e.getMessage().equalsIgnoreCase("/musicisland controls")){e.setCanceled(true);showControls();}}
        private void showSettings(){mc.displayGuiScreen(new Settings(mc.currentScreen));}
        private void showControls(){if(engine.active()){engine.expand();mc.displayGuiScreen(new Island(mc.currentScreen));}}
        private void draw(boolean menu){
            if(!engine.active())return;
            long now=System.nanoTime();
            if(texture==null){pixels=new NativeImage(PortableCanvas.WIDTH*PortableCanvas.DENSITY,PortableCanvas.HEIGHT*PortableCanvas.DENSITY,false);texture=new DynamicTexture(pixels);mc.getTextureManager().loadTexture(id,texture);}
            if(now-lastUpload>=16000000L){BufferedImage image=engine.draw(mc.mainWindow.getScaledWidth(),mc.mainWindow.getScaledHeight(),menu);if(image==null)return;
                int[] data=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
                for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int argb=data[y*image.getWidth()+x];pixels.setPixelRGBA(x,y,(argb&0xFF00FF00)|((argb>>>16)&255)|((argb&255)<<16));}
                texture.updateDynamicTexture();lastUpload=now;
            }
            GlStateManager.pushMatrix();
            try{GlStateManager.translatef(engine.center-PortableCanvas.WIDTH*engine.scale*.5f,engine.top-12*engine.scale,0);GlStateManager.scalef(engine.scale,engine.scale,1);
                GlStateManager.enableBlend();GlStateManager.blendFuncSeparate(770,771,1,771);GlStateManager.color4f(1,1,1,1);mc.getTextureManager().bindTexture(id);
                Gui.drawScaledCustomSizeModalRect(0,0,0,0,pixels.getWidth(),pixels.getHeight(),PortableCanvas.WIDTH,PortableCanvas.HEIGHT,pixels.getWidth(),pixels.getHeight());
            }finally{GlStateManager.popMatrix();GlStateManager.disableBlend();}
        }
        private final class Island extends GuiScreen {
            private final GuiScreen parent; Island(GuiScreen parent){this.parent=parent;}
            @Override public boolean doesGuiPauseGame(){return false;}
            @Override public void render(int x,int y,float delta){draw(false);drawCenteredString(fontRenderer,"M / Escape to close | F8 settings | arrows control playback",width/2,height-20,0xB8B8C2);}
            @Override public boolean mouseClicked(double x,double y,int b){if(!engine.press(x,y,b)&&b==0)close();return true;}
            @Override public boolean mouseReleased(double x,double y,int b){engine.release(x,y,b);return true;}
            @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){engine.drag(x,y);return true;}
            @Override public boolean keyPressed(int k,int sc,int mods){
                if(controls.matchesKey(k,sc)||down.matchesKey(k,sc)&&engine.config.arrows){close();return true;}
                if(settings.matchesKey(k,sc)){showSettings();return true;}
                if(engine.config.arrows){if(up.matchesKey(k,sc)){engine.action("toggle");return true;}if(left.matchesKey(k,sc)){engine.action("previous");return true;}if(right.matchesKey(k,sc)){engine.action("next");return true;}}
                return super.keyPressed(k,sc,mods);
            }
            @Override public void close(){engine.dismiss();mc.displayGuiScreen(parent);}
            @Override public void onGuiClosed(){engine.cancelGesture();}
        }
        private final class Settings extends GuiScreen {
            private final GuiScreen parent; private int page;
            Settings(GuiScreen parent){this.parent=parent;}
            @Override public boolean doesGuiPauseGame(){return false;}
            private void rebuild(){buttons.clear();children.clear();initGui();}
            private void button(int x,int y,int w,String text,Runnable action){addButton(new GuiButton(0,x,y,w,20,text){@Override public void onClick(double a,double b){action.run();}});}
            @Override protected void initGui(){
                int rows=Math.max(2,Math.min(7,(height-92)/24)),pages=(SettingsRows.COUNT+rows-1)/rows;page=Math.min(page,pages-1);
                for(int n=0;n<rows&&page*rows+n<SettingsRows.COUNT;n++){int index=page*rows+n,y=34+n*24;
                    button(width/2-152,y,24,"-",()->{SettingsRows.change(engine.config,index,-1);rebuild();});
                    button(width/2-124,y,276,SettingsRows.label(engine.config,index),()->{SettingsRows.change(engine.config,index,1);rebuild();});}
                button(width/2-152,height-44,100,"Page "+(page+1)+" / "+pages,()->{page=(page+1)%pages;rebuild();});
                button(width/2-48,height-44,132,"Playback controls",()->showControls());
                button(width/2+88,height-44,64,"Done",()->close());
            }
            @Override public void render(int x,int y,float d){Gui.drawRect(0,0,width,height,0xD0202028);super.render(x,y,d);drawCenteredString(fontRenderer,"MusicIsland settings",width/2,12,0xFFFFFF);drawCenteredString(fontRenderer,"Configure keys in Minecraft Options > Controls",width/2,height-18,0xB8B8C2);}
            @Override public void close(){engine.config.save();mc.displayGuiScreen(parent);}
        }
    }
}
