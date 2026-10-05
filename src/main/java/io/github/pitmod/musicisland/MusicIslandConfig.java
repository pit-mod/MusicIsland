package io.github.pitmod.musicisland;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.*;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class MusicIslandConfig extends Config {
    @Switch(name="Show in Minecraft menus", description="Allow the island outside a loaded game, including the title screen and server browser.", category="Appearance")
    public boolean showInMenus=false;
    @Slider(name="Scale", min=0.6f, max=2.5f, category="Appearance")
    public float scale=1;
    @Slider(name="Top offset", min=0, max=200, step=1, category="Appearance")
    public int offset=8;
    @Slider(name="Horizontal offset", min=-600, max=600, step=1, category="Appearance")
    public int horizontalOffset=0;
    @Switch(name="Compact song title", category="Appearance")
    public boolean compactTitle=true;
    @Switch(name="Audio visualizer", category="Appearance")
    public boolean indicator=true;
    @Dropdown(name="Motion", options={"Fluid", "Reduced", "Off"}, category="Appearance")
    public int motion=0;
    @Dropdown(name="Preferred media source", options={"Playing", "Spotify", "Current"}, category="Playback")
    public int source=0;
    @Slider(name="Track announcement seconds", min=0, max=10, category="Playback")
    public float announcement=2;
    @Slider(name="Auto collapse seconds", min=0, max=120, step=1, description="0 keeps the island expanded until you close it.", category="Playback")
    public int collapse=0;
    @Slider(name="Paused visibility seconds", min=0, max=120, step=1, description="0 keeps paused media visible.", category="Playback")
    public int paused=15;
    @KeyBind(name="Open music controls", category="Controls")
    public OneKeyBind interact=new OneKeyBind(Keyboard.KEY_M);
    @Switch(name="Arrow key controls", description="Down: expand/close. Up: play/pause. Left/right: previous/next.", category="Controls")
    public boolean arrows=true;
    @Switch(name="Preview", description="Simulated music for adjusting appearance without real playback.", category="Preview")
    public boolean preview=false;
    @Button(name="Restart preview", text="Restart", category="Preview")
    public transient Runnable restartPreview=()->{if(MusicIslandMod.island!=null)MusicIslandMod.island.restartPreview();};
    @Button(name="Open music controls", text="Open", category="Controls")
    public transient Runnable openControls=()->{if(MusicIslandMod.island!=null)MusicIslandMod.island.open();};
    @Button(name="Restore defaults", text="Reset", category="Appearance")
    public transient Runnable resetDefaults=this::resetDefaults;

    public MusicIslandConfig(){
        super(new Mod("MusicIsland",ModType.HUD,"/assets/musicisland/logo.png"),"musicisland.json",true);
        initialize();
        registerKeyBind(interact,()->{if(Minecraft.getMinecraft().currentScreen==null&&MusicIslandMod.island!=null)MusicIslandMod.island.open();});
    }
    public String motionName(){return new String[]{"Fluid","Reduced","Off"}[Math.max(0,Math.min(2,motion))];}
    public String sourceName(){return new String[]{"Playing","Spotify","Current"}[Math.max(0,Math.min(2,source))];}
    private void resetDefaults(){
        scale=1;offset=8;horizontalOffset=0;compactTitle=true;indicator=true;motion=0;source=0;showInMenus=false;
        announcement=2;collapse=0;paused=15;arrows=true;preview=false;
        interact.clearKeys();interact.addKey(Keyboard.KEY_M);save();
    }
}
