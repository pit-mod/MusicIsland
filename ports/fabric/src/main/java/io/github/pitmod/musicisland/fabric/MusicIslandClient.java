package io.github.pitmod.musicisland.fabric;

import org.lwjgl.glfw.GLFW;
import net.minecraft.client.texture.NativeImage;
import io.github.pitmod.musicisland.portable.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Fabric adapter using Minecraft-native textures and input. */
public final class MusicIslandClient implements ClientModInitializer {
    private static final Identifier TEXTURE=new Identifier("musicisland","hud");
    private final MinecraftClient mc=MinecraftClient.getInstance();
    private PortableEngine engine;
    private NativeImageBackedTexture texture;
    private NativeImage pixels;
    private KeyBinding controls,settings,down,up,left,right;
    private Screen priorScreen;
    private long lastUpload;

    @Override public void onInitializeClient() {
        engine=new PortableEngine(PortableSettings.load(FabricLoader.getInstance().getConfigDir().resolve("musicisland.json")));
        initializeScreenAccess();
        String category="category.musicisland";
        controls=key("controls","KEY_M",category);settings=key("settings","KEY_F8",category);
        down=key("expand","KEY_DOWN",category);up=key("play_pause","KEY_UP",category);
        left=key("previous","KEY_LEFT",category);right=key("next","KEY_RIGHT",category);
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            engine.tick(client.world!=null&&client.player!=null);
            Screen screen=currentScreen();
            if(screen!=priorScreen){engine.cancelGesture();priorScreen=screen;}
            if(!engine.active()&&texture!=null)releaseTexture();
            while(settings.wasPressed())if(screen==null)showSettings();
            while(controls.wasPressed())if(screen==null&&engine.active())showControls();
            while(down.wasPressed())if(screen==null&&engine.active()&&engine.config.arrows)toggleControls();
            while(up.wasPressed())if(screen==null&&engine.config.arrows)engine.action("toggle");
            while(left.wasPressed())if(screen==null&&engine.config.arrows)engine.action("previous");
            while(right.wasPressed())if(screen==null&&engine.config.arrows)engine.action("next");
        });
        HudRenderCallback.EVENT.register((graphics,delta)->{
            if(currentScreen()==null)draw(graphics,false);
        });
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof IslandScreen||screen instanceof SettingsScreen)return;
            ScreenEvents.afterRender(screen).register((s,g,x,y,delta)->draw(g,true));
            ScreenMouseEvents.allowMouseClick(screen).register((s,x,y,button)->!engine.press(x,y,button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s,x,y,button)->{
                if(!engine.ownsPointer())return true;engine.release(x,y,button);return false;
            });

        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->dispatcher.register(literal("musicisland")
            .executes(c->{showSettings();return 1;}).then(literal("controls").executes(c->{showControls();return 1;}))));
        Runtime.getRuntime().addShutdownHook(new Thread(engine::close,"MusicIsland shutdown"));
    }
    private KeyBinding key(String name,String constant,String category) {
        try{return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.musicisland."+name,GLFW.class.getField("GLFW_"+constant).getInt(null),category));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name){return LiteralArgumentBuilder.literal(name);}
    private void initializeScreenAccess(){}
    private Screen currentScreen(){return mc.currentScreen;}
    private void setScreen(Screen s){engine.cancelGesture();mc.setScreen(s);priorScreen=currentScreen();lastUpload=0;}
    private void showControls(){if(engine.active()){engine.expand();setScreen(new IslandScreen(currentScreen()));}}
    private void toggleControls(){if(engine.presentation.explicit)engine.dismiss();else showControls();}
    private void showSettings(){setScreen(new SettingsScreen(currentScreen()));}
    private void releaseTexture(){mc.getTextureManager().destroyTexture(TEXTURE);texture=null;pixels=null;lastUpload=0;}
    private void draw(DrawContext graphics,boolean menu){
        if(!engine.active())return;
        long now=System.nanoTime();
        if(texture==null||now-lastUpload>=16000000L){
            BufferedImage image=engine.draw(graphics.getScaledWindowWidth(),graphics.getScaledWindowHeight(),menu,(double)mc.getWindow().getFramebufferWidth()/graphics.getScaledWindowWidth());if(image==null)return;
            if(pixels==null||pixels.getWidth()!=image.getWidth()||pixels.getHeight()!=image.getHeight()){
                if(texture!=null)releaseTexture();
                pixels=new NativeImage(image.getWidth(),image.getHeight(),false);
                texture=new NativeImageBackedTexture(pixels);mc.getTextureManager().registerTexture(TEXTURE,texture);
            }
            int[] data=((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels.setColor(x,y,toABGR(data[y*image.getWidth()+x]));
            texture.upload();texture.setFilter(true,false);lastUpload=now;
        }
        graphics.getMatrices().push();
        try{
            graphics.getMatrices().translate(engine.center-PortableCanvas.WIDTH*engine.scale*.5f,engine.top-12*engine.scale,0);
            graphics.getMatrices().scale(engine.scale,engine.scale,1);
            graphics.drawTexture(TEXTURE,0,0,PortableCanvas.WIDTH,PortableCanvas.HEIGHT,0,0,pixels.getWidth(),pixels.getHeight(),pixels.getWidth(),pixels.getHeight());
        }finally{graphics.getMatrices().pop();}
    }
    private static int toABGR(int argb){return (argb&0xFF00FF00)|((argb>>>16)&255)|((argb&255)<<16);}
    private final class IslandScreen extends Screen {
        private final Screen parent;
        IslandScreen(Screen parent){super(Text.literal("MusicIsland"));this.parent=parent;}
        @Override public boolean shouldPause(){return false;}
        @Override public void render(DrawContext g,int x,int y,float delta){
            draw(g,false);g.drawCenteredTextWithShadow(textRenderer,"M / Escape to close · F8 settings · arrow keys control playback",width/2,height-20,0xFFB8B8C2);
        }
        @Override public boolean mouseClicked(double x,double y,int button){
            if(engine.press(x,y,button))return true;if(button==0)close();return true;
        }
        @Override public boolean mouseReleased(double x,double y,int button){engine.release(x,y,button);return true;}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){engine.drag(x,y);return true;}
        @Override public boolean keyPressed(int key,int scancode,int modifiers){
            if(controls.matchesKey(key,scancode)||down.matchesKey(key,scancode)&&engine.config.arrows){close();return true;}
            if(settings.matchesKey(key,scancode)){showSettings();return true;}
            if(engine.config.arrows){if(up.matchesKey(key,scancode)){engine.action("toggle");return true;}if(left.matchesKey(key,scancode)){engine.action("previous");return true;}if(right.matchesKey(key,scancode)){engine.action("next");return true;}}
            return super.keyPressed(key,scancode,modifiers);
        }
        @Override public void close(){engine.dismiss();setScreen(parent);}
        @Override public void removed(){engine.cancelGesture();}
    }
    private final class SettingsScreen extends Screen {
        private final Screen parent;private int page;
        SettingsScreen(Screen parent){super(Text.literal("MusicIsland settings"));this.parent=parent;}
        @Override public boolean shouldPause(){return false;}
        @Override protected void init(){
            int rows=Math.max(2,Math.min(7,(height-92)/24)),pages=(SettingsRows.COUNT+rows-1)/rows;page=Math.min(page,pages-1);
            for(int n=0;n<rows&&page*rows+n<SettingsRows.COUNT;n++){
                int index=page*rows+n,y=34+n*24;
                addDrawableChild(ButtonWidget.builder(Text.literal("−"),b->{SettingsRows.change(engine.config,index,-1);clearAndInit();}).dimensions(width/2-152,y,24,20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal(SettingsRows.label(engine.config,index)),b->{SettingsRows.change(engine.config,index,1);clearAndInit();}).dimensions(width/2-124,y,276,20).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("Page "+(page+1)+" / "+pages),b->{page=(page+1)%pages;clearAndInit();}).dimensions(width/2-152,height-44,100,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Playback controls"),b->showControls()).dimensions(width/2-48,height-44,132,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b->close()).dimensions(width/2+88,height-44,64,20).build());
        }
        @Override public void render(DrawContext g,int x,int y,float delta){
            g.fill(0,0,width,height,0xD0202028);super.render(g,x,y,delta);
            g.drawCenteredTextWithShadow(textRenderer,"MusicIsland settings",width/2,12,0xFFFFFFFF);
            g.drawCenteredTextWithShadow(textRenderer,"Configure keys in Minecraft Options → Controls",width/2,height-18,0xFFB8B8C2);
        }
        @Override public void close(){engine.config.save();setScreen(parent);}
    }
}
