package io.github.pitmod.musicisland.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.pitmod.musicisland.portable.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** A single binary for the 26.x native GUI API, including the Vulkan backend. */
public final class MusicIslandClient implements ClientModInitializer {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("musicisland","hud");
    private final Minecraft mc=Minecraft.getInstance();
    private PortableEngine engine;
    private DynamicTexture texture;
    private NativeImage pixels;
    private KeyMapping controls,settings,down,up,left,right;
    private Screen priorScreen;
    private Field screenField;
    private Field guiField;
    private Method screenGetter,screenSetter;
    private Object guiOwner;
    private long lastUpload;

    @Override public void onInitializeClient() {
        engine=new PortableEngine(PortableSettings.load(FabricLoader.getInstance().getConfigDir().resolve("musicisland.json")));
        initializeScreenAccess();
        KeyMapping.Category category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("musicisland","controls"));
        controls=key("controls","KEY_M",category);settings=key("settings","KEY_F8",category);
        down=key("expand","KEY_DOWN",category);up=key("play_pause","KEY_UP",category);
        left=key("previous","KEY_LEFT",category);right=key("next","KEY_RIGHT",category);
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            engine.tick(client.level!=null&&client.player!=null);
            Screen screen=currentScreen();
            if(screen!=priorScreen){engine.cancelGesture();priorScreen=screen;}
            if(!engine.active()&&texture!=null)releaseTexture();
            while(settings.consumeClick())if(screen==null)showSettings();
            while(controls.consumeClick())if(screen==null&&engine.active())showControls();
            while(down.consumeClick())if(screen==null&&engine.active()&&engine.config.arrows)toggleControls();
            while(up.consumeClick())if(screen==null&&engine.config.arrows)engine.action("toggle");
            while(left.consumeClick())if(screen==null&&engine.config.arrows)engine.action("previous");
            while(right.consumeClick())if(screen==null&&engine.config.arrows)engine.action("next");
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("musicisland","island"),(graphics,delta)->{
            if(currentScreen()==null)draw(graphics,false);
        });
        ScreenEvents.AFTER_INIT.register((client,screen,w,h)->{
            if(screen instanceof IslandScreen||screen instanceof SettingsScreen)return;
            ScreenEvents.afterExtract(screen).register((s,g,x,y,delta)->draw(g,true));
            ScreenMouseEvents.allowMouseClick(screen).register((s,e)->!engine.press(e.x(),e.y(),e.button()));
            ScreenMouseEvents.allowMouseRelease(screen).register((s,e)->{
                if(!engine.ownsPointer())return true;engine.release(e.x(),e.y(),e.button());return false;
            });
            ScreenMouseEvents.allowMouseDrag(screen).register((s,e,dx,dy)->{
                if(!engine.ownsPointer())return true;engine.drag(e.x(),e.y());return false;
            });
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->dispatcher.register(literal("musicisland")
            .executes(c->{showSettings();return 1;}).then(literal("controls").executes(c->{showControls();return 1;}))));
        Runtime.getRuntime().addShutdownHook(new Thread(engine::close,"MusicIsland shutdown"));
    }
    private KeyMapping key(String name,String constant,KeyMapping.Category category) {
        // 26.3 switched from GLFW key symbols to SDL scancodes; do not inline constants.
        try{return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.musicisland."+name,InputConstants.class.getField(constant).getInt(null),category));}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported input API",e);}
    }
    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name){return LiteralArgumentBuilder.literal(name);}
    private void initializeScreenAccess(){
        try{screenField=Minecraft.class.getField("screen");screenSetter=Minecraft.class.getMethod("setScreen",Screen.class);guiOwner=mc;}
        catch(NoSuchFieldException e){
            try{guiField=Minecraft.class.getField("gui");screenGetter=guiField.getType().getMethod("screen");screenSetter=guiField.getType().getMethod("setScreen",Screen.class);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Unsupported screen API",failure);}
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported screen API",e);}
    }
    private Screen currentScreen(){
        try{Object owner=guiField==null?guiOwner:guiField.get(mc);return owner==null?null:(Screen)(screenField!=null?screenField.get(mc):screenGetter.invoke(owner));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void setScreen(Screen s){try{screenSetter.invoke(guiField==null?guiOwner:guiField.get(mc),s);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private void showControls(){if(engine.active()){engine.expand();setScreen(new IslandScreen(currentScreen()));}}
    private void toggleControls(){if(engine.presentation.explicit)engine.dismiss();else showControls();}
    private void showSettings(){setScreen(new SettingsScreen(currentScreen()));}
    private void releaseTexture(){mc.getTextureManager().release(TEXTURE);texture=null;pixels=null;lastUpload=0;}
    private void draw(GuiGraphicsExtractor graphics,boolean menu){
        if(!engine.active())return;
        long now=System.nanoTime();
        if(texture==null){pixels=new NativeImage(PortableCanvas.WIDTH*PortableCanvas.DENSITY,PortableCanvas.HEIGHT*PortableCanvas.DENSITY,false);
            texture=new DynamicTexture(()->"MusicIsland HUD",pixels);mc.getTextureManager().register(TEXTURE,texture);}
        if(now-lastUpload>=16000000L){
            BufferedImage image=engine.draw(graphics.guiWidth(),graphics.guiHeight(),menu);if(image==null)return;
            int[] data=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)pixels.setPixel(x,y,data[y*image.getWidth()+x]);
            texture.upload();lastUpload=now;
        }
        graphics.pose().pushMatrix();
        try{
            graphics.pose().translate(engine.center-PortableCanvas.WIDTH*engine.scale*.5f,engine.top-12*engine.scale);
            graphics.pose().scale(engine.scale,engine.scale);
            graphics.blit(TEXTURE,0,0,PortableCanvas.WIDTH,PortableCanvas.HEIGHT,0,1,0,1);
        }finally{graphics.pose().popMatrix();}
    }
    private final class IslandScreen extends Screen {
        private final Screen parent;
        IslandScreen(Screen parent){super(Component.literal("MusicIsland"));this.parent=parent;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
            draw(g,false);g.centeredText(font,"M / Escape to close · F8 settings · arrow keys control playback",width/2,height-20,0xFFB8B8C2);
        }
        @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
            if(engine.press(e.x(),e.y(),e.button()))return true;if(e.button()==0)onClose();return true;
        }
        @Override public boolean mouseReleased(MouseButtonEvent e){engine.release(e.x(),e.y(),e.button());return true;}
        @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){engine.drag(e.x(),e.y());return true;}
        @Override public boolean keyPressed(KeyEvent e){
            if(controls.matches(e)||down.matches(e)&&engine.config.arrows){onClose();return true;}
            if(settings.matches(e)){showSettings();return true;}
            if(engine.config.arrows){if(up.matches(e)){engine.action("toggle");return true;}if(left.matches(e)){engine.action("previous");return true;}if(right.matches(e)){engine.action("next");return true;}}
            return super.keyPressed(e);
        }
        @Override public void onClose(){engine.dismiss();setScreen(parent);}
        @Override public void removed(){engine.cancelGesture();}
    }
    private final class SettingsScreen extends Screen {
        private final Screen parent;private int page;
        SettingsScreen(Screen parent){super(Component.literal("MusicIsland settings"));this.parent=parent;}
        @Override public boolean isPauseScreen(){return false;}
        @Override protected void init(){
            int rows=Math.max(2,Math.min(7,(height-92)/24)),pages=(SettingsRows.COUNT+rows-1)/rows;page=Math.min(page,pages-1);
            for(int n=0;n<rows&&page*rows+n<SettingsRows.COUNT;n++){
                int index=page*rows+n,y=34+n*24;
                addRenderableWidget(Button.builder(Component.literal("−"),b->{SettingsRows.change(engine.config,index,-1);rebuildWidgets();}).bounds(width/2-152,y,24,20).build());
                addRenderableWidget(Button.builder(Component.literal(SettingsRows.label(engine.config,index)),b->{SettingsRows.change(engine.config,index,1);rebuildWidgets();}).bounds(width/2-124,y,276,20).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Page "+(page+1)+" / "+pages),b->{page=(page+1)%pages;rebuildWidgets();}).bounds(width/2-152,height-44,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("Playback controls"),b->showControls()).bounds(width/2-48,height-44,132,20).build());
            addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width/2+88,height-44,64,20).build());
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
            g.fill(0,0,width,height,0xD0202028);super.extractRenderState(g,x,y,delta);
            g.centeredText(font,"MusicIsland settings",width/2,12,0xFFFFFFFF);
            g.centeredText(font,"Configure keys in Minecraft Options → Controls",width/2,height-18,0xFFB8B8C2);
        }
        @Override public void onClose(){engine.config.save();setScreen(parent);}
    }
}
