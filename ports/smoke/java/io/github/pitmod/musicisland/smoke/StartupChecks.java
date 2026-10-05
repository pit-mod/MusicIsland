package io.github.pitmod.musicisland.smoke;
import io.github.pitmod.musicisland.portable.PortableEngine;
import io.github.pitmod.musicisland.music.MediaSnapshot;
import io.github.pitmod.musicisland.music.MusicLayout;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import java.lang.reflect.*;
import java.nio.file.*;
/** Boot/render/menu check in an isolated game directory, without a user account. */
public final class StartupChecks implements ClientModInitializer {
    private Object adapter; private PortableEngine engine; private int ticks,stage; private long started; private boolean finished,wasPlaying;
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
                    engine.config.preview=true;engine.config.showInMenus=true;engine.config.scale=2.5f;started=System.nanoTime();
                }
                ticks++;
                if(System.nanoTime()-started>90000000000L)throw new AssertionError("No rendered island within 90 seconds");
                if(stage==0&&ticks>40&&((Long)field("lastUpload"))>0){
                    if(!engine.active()||engine.presentation.explicit)throw new AssertionError("Preview or manual expansion broken");
                    invoke("showControls");stage=1;ticks=0;
                }else if(stage==1&&ticks>30){
                    if(!engine.presentation.explicit||engine.presentation.reveal.getCurrentValue()<.9)throw new AssertionError("Expansion did not render");
                    screenshot(client);
                    MediaSnapshot media=displayed();wasPlaying=engine.presentation.controls.playing(media);
                    mouse("mouseClicked",81,MusicLayout.CONTROL_Y,false);
                    if(!engine.ownsPointer())throw new AssertionError("Native left click did not reach the pause button");
                    stage=5;ticks=0;
                }else if(stage==5&&ticks>1){
                    if(!engine.ownsPointer())throw new AssertionError("Screen change canceled a live mouse gesture");
                    mouse("mouseReleased",81,MusicLayout.CONTROL_Y,false);
                    if(engine.presentation.controls.playing(displayed())==wasPlaying)throw new AssertionError("Native pause click did not toggle playback");
                    click(81+MusicLayout.CONTROL_SPACING,MusicLayout.CONTROL_Y);
                    if(engine.presentation.controls.direction!=1)throw new AssertionError("Native next click failed");
                    stage=6;ticks=0;
                }else if(stage==6&&ticks>2){
                    click(81-MusicLayout.CONTROL_SPACING,MusicLayout.CONTROL_Y);
                    if(engine.presentation.controls.direction!=-1)throw new AssertionError("Native previous click failed");
                    stage=7;ticks=0;
                }else if(stage==7&&ticks>2){
                    click(MusicLayout.SEEK_X+MusicLayout.SEEK_WIDTH*.75,MusicLayout.SEEK_Y);
                    stage=8;ticks=0;
                }else if(stage==8&&ticks>2){
                    MediaSnapshot media=displayed();
                    if(Math.abs(media.position/media.duration-.75)>.03)throw new AssertionError("Native timeline seek failed");
                    boolean before=engine.presentation.controls.playing(media);
                    mouse("mouseClicked",81,MusicLayout.CONTROL_Y,true);mouse("mouseReleased",81,MusicLayout.CONTROL_Y,true);
                    if(engine.ownsPointer()||engine.presentation.controls.playing(media)!=before)throw new AssertionError("Non-left click triggered playback");
                    invoke("showSettings");stage=2;ticks=0;
                }else if(stage==2&&ticks>15){
                    invoke("showControls");engine.dismiss();stage=3;ticks=0;
                }else if(stage==3&&ticks>30){
                    if(engine.presentation.reveal.getCurrentValue()>.1)throw new AssertionError("Collapse did not finish");
                    engine.config.showInMenus=false;stage=4;ticks=0;
                }else if(stage==4&&ticks>4){
                    if(engine.active()||field("texture")!=null)throw new AssertionError("Menu visibility or texture cleanup broken");
                    Files.write(FabricLoader.getInstance().getGameDir().resolve("musicisland-smoke-pass.txt"),
                        "PASS: client boot, scaled texture upload, native mouse pause/previous/next/seek, non-left click filtering, settings, collapse and default menu hiding".getBytes("UTF-8"));
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
    private MediaSnapshot displayed()throws Exception{Field f=PortableEngine.class.getDeclaredField("displayed");f.setAccessible(true);return (MediaSnapshot)f.get(engine);}
    private void click(double x,double y)throws Exception{mouse("mouseClicked",x,y,false);mouse("mouseReleased",x,y,false);}
    private void mouse(String name,double localX,double localY,boolean other)throws Exception{
        Method getter=adapter.getClass().getDeclaredMethod("currentScreen");getter.setAccessible(true);Object screen=getter.invoke(adapter);
        double x=engine.center+(localX-81)*engine.scale,y=engine.top+localY*engine.scale;
        int left=0;try{left=Class.forName("com.mojang.blaze3d.platform.InputConstants").getField("MOUSE_BUTTON_LEFT").getInt(null);}catch(ClassNotFoundException ignored){}
        int button=other?left+1:left;
        for(Method m:screen.getClass().getDeclaredMethods())if(m.getName().equals(name)){
            m.setAccessible(true);Class<?>[] p=m.getParameterTypes();
            if(p.length==3){m.invoke(screen,x,y,button);return;}
            Constructor<?> event=p[0].getConstructors()[0];Class<?> infoType=event.getParameterTypes()[2];
            Object info=infoType.getConstructor(int.class,int.class).newInstance(button,0);
            Object e=event.newInstance(x,y,info);
            if(p.length==2)m.invoke(screen,e,false);else m.invoke(screen,e);return;
        }
        throw new AssertionError("Native mouse method not found: "+name);
    }
    private void screenshot(Object client)throws Exception{
        if(!client.getClass().getName().equals("net.minecraft.client.Minecraft"))return;
        Object target;
        try{target=client.getClass().getMethod("getMainRenderTarget").invoke(client);}
        catch(NoSuchMethodException e){Object renderer=client.getClass().getField("gameRenderer").get(client);target=renderer.getClass().getMethod("mainRenderTarget").invoke(renderer);}
        Class<?> capture=Class.forName("net.minecraft.client.Screenshot");
        for(Method m:capture.getMethods())if(m.getName().equals("grab")&&m.getParameterCount()==5){
            java.util.function.Consumer<Object> message=v->System.out.println("MusicIsland render captured: "+v);
            m.invoke(null,FabricLoader.getInstance().getGameDir().toFile(),"musicisland-expanded.png",target,1,message);return;
        }
        throw new AssertionError("Screenshot API not found");
    }
}
