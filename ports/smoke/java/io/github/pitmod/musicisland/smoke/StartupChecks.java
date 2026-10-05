package io.github.pitmod.musicisland.smoke;
import io.github.pitmod.musicisland.portable.PortableEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.*;
import java.nio.file.*;
/** Boot/render/menu check in an isolated game directory, without a user account. */
public final class StartupChecks implements ClientModInitializer {
    private Object adapter; private PortableEngine engine; private int ticks,stage; private long started; private boolean finished;
    public void onInitializeClient(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(finished)return;
            try{
                if(adapter==null){
                    for(Object entry:FabricLoader.getInstance().getEntrypoints("client",ClientModInitializer.class))
                        if(entry.getClass().getName().endsWith("fabric.MusicIslandClient"))adapter=entry;
                    if(adapter==null)throw new AssertionError("Missing MusicIsland entrypoint");
                    engine=(PortableEngine)field("engine");
                    if(engine.config.showInMenus)throw new AssertionError("Menus must be disabled by default");
                    engine.config.preview=true;engine.config.showInMenus=true;started=System.nanoTime();
                }
                ticks++;
                if(System.nanoTime()-started>90000000000L)throw new AssertionError("No rendered island within 90 seconds");
                if(stage==0&&ticks>40&&((Long)field("lastUpload"))>0){
                    if(!engine.active()||engine.presentation.explicit)throw new AssertionError("Preview or manual expansion broken");
                    invoke("showControls");stage=1;ticks=0;
                }else if(stage==1&&ticks>30){
                    if(!engine.presentation.explicit||engine.presentation.reveal.getCurrentValue()<.9)throw new AssertionError("Expansion did not render");
                    engine.action("toggle");engine.action("previous");engine.action("next");
                    invoke("showSettings");stage=2;ticks=0;
                }else if(stage==2&&ticks>15){
                    invoke("showControls");engine.dismiss();stage=3;ticks=0;
                }else if(stage==3&&ticks>30){
                    if(engine.presentation.reveal.getCurrentValue()>.1)throw new AssertionError("Collapse did not finish");
                    engine.config.showInMenus=false;stage=4;ticks=0;
                }else if(stage==4&&ticks>4){
                    if(engine.active()||field("texture")!=null)throw new AssertionError("Menu visibility or texture cleanup broken");
                    Files.write(FabricLoader.getInstance().getGameDir().resolve("musicisland-smoke-pass.txt"),
                        "PASS: client boot, texture upload, preview, expansion, controls, settings, collapse and default menu hiding".getBytes("UTF-8"));
                    System.out.println("MUSICISLAND_STARTUP_CHECK_PASS");
                    finished=true;
                    Method stop;
                    try{stop=client.getClass().getMethod("scheduleStop");}
                    catch(NoSuchMethodException e){stop=client.getClass().getMethod("stop");}
                    stop.invoke(client);
                }
            }catch(Throwable e){e.printStackTrace();System.err.println("MUSICISLAND_STARTUP_CHECK_FAIL");System.exit(2);}
        });
    }
    private Object field(String name)throws Exception{Field f=adapter.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(adapter);}
    private void invoke(String name)throws Exception{Method m=adapter.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(adapter);}
}
