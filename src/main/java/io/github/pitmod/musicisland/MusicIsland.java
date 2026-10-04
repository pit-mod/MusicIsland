package io.github.pitmod.musicisland;

import io.github.pitmod.musicisland.music.*;
import io.github.pitmod.musicisland.utils.ScreenScale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import org.lwjgl.input.*;

/** A fresh, capability-aware Windows music HUD. Screens never own its provider lifetime. */
public final class MusicIsland {
    private final net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();
    public final MusicIslandConfig config;
    private boolean active;
    private final MusicPresentation presentation=new MusicPresentation();
    private final MusicGesture gesture=new MusicGesture();
    private final MusicIslandRenderer renderer=new MusicIslandRenderer();
    private final MusicPreview.Controller previewPlayer=new MusicPreview.Controller();
    private volatile WindowsMusicProvider provider;
    private MediaSnapshot displayed;
    private long previewStart=System.nanoTime();
    private float cx,top,actualScale=1;
    private GuiScreen pointerScreen;
    private int lastPointerX,lastPointerY;
    private boolean heldExpanded;
    public MusicIsland(MusicIslandConfig config){this.config=config;}
    public boolean isToggled(){return config.enabled&&(config.showInMenus||(mc.theWorld!=null&&mc.thePlayer!=null));}
    public boolean isInteractionKey(int code){return config.interact.getKeyBinds().size()==1&&config.interact.getKeyBinds().contains(code);}
    public void restartPreview(){previewPlayer.reset();previewStart=System.nanoTime();config.preview=true;config.save();}
    @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)return;
        if(isToggled()&&!active){active=true;provider=new WindowsMusicProvider();previewStart=System.nanoTime();}
        else if(!isToggled()&&active)shutdown();
    }
    public void shutdown(){active=false;if(provider!=null){provider.close();provider=null;}gesture.cancel();heldExpanded=false;presentation.dismiss();presentation.controls.reset();displayed=null;
        if(mc.currentScreen instanceof MusicInteractionScreen)((MusicInteractionScreen)mc.currentScreen).finish();renderer.close();}
    public void closeProvider(){WindowsMusicProvider p=provider;if(p!=null)p.close();}
    @SubscribeEvent public void keyboard(net.minecraftforge.fml.common.gameevent.InputEvent.KeyInputEvent e){
        if(mc.currentScreen==null&&Keyboard.getEventKeyState()&&!Keyboard.isRepeatEvent())arrowKey(Keyboard.getEventKey());
    }
    public void open(){if(!isToggled())return;gesture.cancel();presentation.expand(System.nanoTime());mc.displayGuiScreen(new MusicInteractionScreen(this,mc.currentScreen));}
    public void dismiss(){gesture.cancel();gesture.clearPending();heldExpanded=false;presentation.dismiss();}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e){if(!isToggled()||e.type!=RenderGameOverlayEvent.ElementType.CHAT||mc.currentScreen!=null)return;draw();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void guiDraw(GuiScreenEvent.DrawScreenEvent.Post e){if(!isToggled()||e.gui instanceof MusicInteractionScreen||cc.polyfrost.oneconfig.gui.OneConfigGui.isOpen())return;draw();}
    public void draw(){if(!isToggled())return;long now=System.nanoTime();boolean synthetic=config.preview;
        if(provider!=null)provider.preference(config.sourceName());
        MediaSnapshot latest=synthetic?previewPlayer.get((now-previewStart)*1e-9,now):provider==null?null:provider.snapshot();
        // Retain the last metadata briefly while the provider reconnects, never use it for commands.
        gesture.reconcile(latest);if(pointerScreen!=null&&pointerScreen!=mc.currentScreen){gesture.cancel();heldExpanded=false;pointerScreen=null;}
        presentation.update(latest,now,config.announcement,config.collapse,config.paused,config.motionName(),synthetic,gesture.owner!=MusicGesture.Owner.NONE,provider!=null&&!provider.error().isEmpty(),gesture.pressedControl(),provider==null?"":provider.error());
        if(latest!=null)displayed=latest;else if(presentation.state==MusicPresentation.State.HIDDEN)displayed=null;
        position();if(gesture.owner==MusicGesture.Owner.EXPAND)drag(lastPointerX,lastPointerY);
        double elapsed=gesture.elapsed(displayed,now,provider!=null&&provider.pending(),provider==null?"":provider.error());
        String status=synthetic?"Preview · controls are simulated":provider==null?"":provider.error();
        if(latest==null&&status.isEmpty())status="Start playback in a media app";
        renderer.draw(cx,top,actualScale,presentation,displayed,elapsed,now,config.indicator&&latest!=null,synthetic,status,gesture.pressedControl(),synthetic||provider==null?MusicAudio.SILENT:provider.audio(),config.compactTitle);
    }
    private int editorTop(){return ScreenScale.get().getScaledWidth()<480?78:56;}
    private float effectiveScale(){return (float)Math.max(.3,Math.min(config.scale,Math.min((ScreenScale.get().getScaledWidth()-16)/MusicLayout.WIDTH,(ScreenScale.get().getScaledHeight()-editorTop()-34)/MusicLayout.HEIGHT)));}
    private void position(){int sw=ScreenScale.get().getScaledWidth(),sh=ScreenScale.get().getScaledHeight();actualScale=effectiveScale();
        cx=Math.max(81*actualScale+6,Math.min(sw-81*actualScale-6,sw/2f+config.horizontalOffset));
        top=Math.max(4,Math.min(config.offset,sh-MusicLayout.HEIGHT*actualScale-6));
        if(mc.currentScreen!=null&&!(mc.currentScreen instanceof MusicInteractionScreen)) {cx=sw*.5f;top=Math.max(4,sh-MusicLayout.HEIGHT*actualScale-28);}
    }
    public boolean hit(int x,int y){return isToggled()&&presentation.visibility.getCurrentValue()>.1&&Math.abs(x-cx)<=presentation.width.getCurrentValue()*actualScale*.5f&&y>=top&&y<=top+presentation.height.getCurrentValue()*actualScale;}
    public boolean press(int x,int y,int button){if(button!=0||!hit(x,y))return false;pointerScreen=mc.currentScreen;lastPointerX=x;lastPointerY=y;
        MediaSnapshot s=displayed; if(s==null){presentation.expand(System.nanoTime());return true;}
        float px=(x-cx)/actualScale+81,py=(y-top)/actualScale;
        MusicGesture.Owner owner=MusicGesture.Owner.EXPAND;
        if(presentation.reveal.getCurrentValue()>.9){
            if(py>=MusicLayout.SEEK_Y-6&&py<=MusicLayout.SEEK_Y+8&&px>=MusicLayout.SEEK_X-6&&px<=MusicLayout.SEEK_X+MusicLayout.SEEK_WIDTH+8&&s.seek)owner=MusicGesture.Owner.SEEK;
            else if(py>=MusicLayout.CONTROL_Y-10&&py<=MusicLayout.CONTROL_Y+10){
                if(Math.abs(px-81)<10){if(!(presentation.controls.playing(s)?s.pause:s.play))return true;owner=MusicGesture.Owner.PLAY;}
                else if(Math.abs(px-(81-MusicLayout.CONTROL_SPACING))<10){if(!s.previous)return true;owner=MusicGesture.Owner.PREVIOUS;}
                else if(Math.abs(px-(81+MusicLayout.CONTROL_SPACING))<10){if(!s.next)return true;owner=MusicGesture.Owner.NEXT;}
            }
            else if(px>=MusicLayout.ART_X&&px<=MusicLayout.ART_X+MusicLayout.ART_SIZE
                    &&py>=MusicLayout.ART_Y&&py<=MusicLayout.ART_Y+MusicLayout.ART_SIZE&&s.open)owner=MusicGesture.Owner.OPEN;
        }
        gesture.begin(owner,s,System.nanoTime());if(owner==MusicGesture.Owner.SEEK)drag(x,y);return true;}
    public void drag(int x,int y){lastPointerX=x;lastPointerY=y;if(gesture.owner==MusicGesture.Owner.SEEK)gesture.drag(((x-cx)/actualScale+81-MusicLayout.SEEK_X)/MusicLayout.SEEK_WIDTH);
        if(gesture.owner==MusicGesture.Owner.EXPAND&&System.nanoTime()-gesture.pressedAt>250000000L&&!heldExpanded&&hit(x,y)){presentation.expand(System.nanoTime());heldExpanded=true;}}
    public void release(int x,int y,int button){if(button!=0||gesture.owner==MusicGesture.Owner.NONE)return;
        if(gesture.owner==MusicGesture.Owner.SEEK)drag(x,y);
        MediaSnapshot target=gesture.target();MusicGesture.Owner owner=gesture.owner;boolean valid=gesture.valid(displayed)&&hit(x,y);
        if(owner==MusicGesture.Owner.SEEK)valid=valid&&(y-top)/actualScale>=MusicLayout.SEEK_Y-8&&(y-top)/actualScale<=MusicLayout.SEEK_Y+10;
        else if(owner!=MusicGesture.Owner.EXPAND){float px=(x-cx)/actualScale+81,py=(y-top)/actualScale;float center=81+(owner==MusicGesture.Owner.PREVIOUS?-MusicLayout.CONTROL_SPACING:owner==MusicGesture.Owner.NEXT?MusicLayout.CONTROL_SPACING:0);valid=valid&&(owner==MusicGesture.Owner.OPEN?py<42:py>=MusicLayout.CONTROL_Y-10&&py<=MusicLayout.CONTROL_Y+10&&Math.abs(px-center)<10);}
        double candidate=gesture.candidate;if(valid&&owner==MusicGesture.Owner.SEEK&&!config.preview&&provider!=null)gesture.retainSeek(System.nanoTime());gesture.cancel();pointerScreen=null;
        if(!valid){if(heldExpanded)presentation.dismiss();heldExpanded=false;return;}
        if(owner==MusicGesture.Owner.EXPAND){presentation.expand(System.nanoTime());presentation.acknowledge();}
        else {String op=owner==MusicGesture.Owner.PLAY?(presentation.controls.playing(target)?"pause":"play"):owner.name().toLowerCase(java.util.Locale.ROOT);send(op,target,candidate);}
        heldExpanded=false;
    }
    private void send(String op,MediaSnapshot target,double value){long now=System.nanoTime();boolean accepted=false;
        if(config.preview){previewPlayer.command(op,target,value,now);accepted=true;}else if(provider!=null)accepted=provider.command(op,target,value);
        if(!accepted&&"seek".equals(op))gesture.clearPending();
        presentation.controls.request(op,target,now,accepted);
        if(accepted&&("play".equals(op)||"pause".equals(op)))presentation.playback.setTarget("play".equals(op)?1:0);
    }
    public boolean arrowKey(int code){
        if(!isToggled()||!config.arrows||!MusicControls.isArrow(code))return false;
        if(code==Keyboard.KEY_DOWN){
            if(mc.currentScreen instanceof MusicInteractionScreen)((MusicInteractionScreen)mc.currentScreen).finish();
            else if(presentation.explicit)dismiss();else open();
            return true;
        }
        MediaSnapshot target=config.preview?displayed:provider==null?null:provider.snapshot();if(target==null)return true;
        String op=code==Keyboard.KEY_UP?(presentation.controls.playing(target)?(target.pause?"pause":null):(target.play?"play":null)):MusicControls.action(code,target);if(op!=null)send(op,target,0);return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void cursor(GuiScreenEvent.MouseInputEvent.Pre e){
        if(!isToggled()||e.gui instanceof MusicInteractionScreen)return;
        int x=Mouse.getEventX()*e.gui.width/Math.max(1,mc.displayWidth),y=e.gui.height-Mouse.getEventY()*e.gui.height/Math.max(1,mc.displayHeight)-1,button=Mouse.getEventButton();
        if(cc.polyfrost.oneconfig.gui.OneConfigGui.isOpen())return;
        if(button==0&&Mouse.getEventButtonState()){if(press(x,y,button))e.setCanceled(true);else if(presentation.explicit){dismiss();e.setCanceled(true);}}
        else if(gesture.owner!=MusicGesture.Owner.NONE){if(button==0)release(x,y,button);else drag(x,y);e.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void cursorKey(GuiScreenEvent.KeyboardInputEvent.Pre e){if(!(e.gui instanceof MusicInteractionScreen)&&isToggled()&&presentation.explicit&&Keyboard.getEventKeyState()&&Keyboard.getEventKey()==Keyboard.KEY_ESCAPE){dismiss();e.setCanceled(true);}}
    @SubscribeEvent public void screenChanged(GuiOpenEvent e){if(e.gui!=pointerScreen){gesture.cancel();heldExpanded=false;pointerScreen=null;}}
    public String fullTitle(){return displayed==null?"":displayed.title+" — "+displayed.artist;}
    public boolean ownsPointer(){return gesture.owner!=MusicGesture.Owner.NONE;}
    public boolean controlsOpen(){return presentation.explicit;}
}
