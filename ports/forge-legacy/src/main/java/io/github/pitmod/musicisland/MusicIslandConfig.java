package io.github.pitmod.musicisland;

import io.github.pitmod.musicisland.portable.PortableSettings;
import io.github.pitmod.musicisland.portable.SettingsRows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Native settings fallback for Forge releases without OneConfig. */
public final class MusicIslandConfig extends PortableSettings {
    private transient final KeyBinding controls=new KeyBinding("MusicIsland controls",Keyboard.KEY_M,"MusicIsland");
    private transient final KeyBinding settings=new KeyBinding("MusicIsland settings",Keyboard.KEY_F8,"MusicIsland");
    public transient final InteractionKey interact=new InteractionKey();
    public MusicIslandConfig(){
        PortableSettings loaded=PortableSettings.load(Minecraft.getMinecraft().mcDataDir.toPath().resolve("config/musicisland.json"));
        try{for(Field f:PortableSettings.class.getFields())if(!Modifier.isStatic(f.getModifiers()))f.set(this,f.get(loaded));}
        catch(IllegalAccessException e){throw new IllegalStateException(e);}
        ClientRegistry.registerKeyBinding(controls);ClientRegistry.registerKeyBinding(settings);
        FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void keyboard(InputEvent.KeyInputEvent event){
        if(Minecraft.getMinecraft().currentScreen!=null)return;
        if(settings.isPressed())openGui();else if(controls.isPressed()&&MusicIslandMod.island!=null)MusicIslandMod.island.open();
    }
    public void openGui(){Minecraft.getMinecraft().displayGuiScreen(new SettingsScreen(Minecraft.getMinecraft().currentScreen));}
    public final class InteractionKey { public List<Integer> getKeyBinds(){return Collections.singletonList(controls.getKeyCode());} }
    public static class SettingsScreen extends GuiScreen {
        private final GuiScreen parent;private int page;
        public SettingsScreen(GuiScreen parent){this.parent=parent;}
        @Override public boolean doesGuiPauseGame(){return false;}
        @Override public void initGui(){
            buttonList.clear();int rows=Math.max(2,Math.min(7,(height-92)/24)),pages=(SettingsRows.COUNT+rows-1)/rows;page=Math.min(page,pages-1);
            for(int n=0;n<rows&&page*rows+n<SettingsRows.COUNT;n++){
                int index=page*rows+n,y=34+n*24;
                buttonList.add(new GuiButton(100+index,width/2-152,y,24,20,"−"));
                buttonList.add(new GuiButton(index,width/2-124,y,276,20,SettingsRows.label(MusicIslandMod.config,index)));
            }
            buttonList.add(new GuiButton(200,width/2-152,height-44,100,20,"Page "+(page+1)+" / "+pages));
            buttonList.add(new GuiButton(201,width/2-48,height-44,132,20,"Playback controls"));
            buttonList.add(new GuiButton(202,width/2+88,height-44,64,20,"Done"));
        }
        @Override protected void actionPerformed(GuiButton button){
            if(button.id<200){SettingsRows.change(MusicIslandMod.config,button.id%100,button.id<100?1:-1);initGui();}
            else if(button.id==200){int rows=Math.max(2,Math.min(7,(height-92)/24));page=(page+1)%((SettingsRows.COUNT+rows-1)/rows);initGui();}
            else if(button.id==201)MusicIslandMod.island.open();else mc.displayGuiScreen(parent);
        }
        @Override public void drawScreen(int x,int y,float delta){
            drawDefaultBackground();drawCenteredString(fontRendererObj,"MusicIsland settings",width/2,12,0xFFFFFF);
            drawCenteredString(fontRendererObj,"Keys: Options > Controls",width/2,height-18,0xB8B8C2);super.drawScreen(x,y,delta);
        }
        @Override protected void keyTyped(char character,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE)mc.displayGuiScreen(parent);else super.keyTyped(character,key);}
        @Override public void onGuiClosed(){MusicIslandMod.config.save();}
    }
}
