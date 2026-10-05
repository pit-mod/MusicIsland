package io.github.pitmod.musicisland.portable;

import io.github.pitmod.musicisland.music.*;
import java.awt.image.BufferedImage;

/** Media, animation and gestures shared by all modern loader adapters. */
public final class PortableEngine implements AutoCloseable {
    public final PortableSettings config;
    public final MusicPresentation presentation = new MusicPresentation();
    private final PortableCanvas canvas = new PortableCanvas();
    private final MusicGesture gesture = new MusicGesture();
    private final MusicPreview.Controller preview = new MusicPreview.Controller();
    private WindowsMusicProvider provider;
    private MediaSnapshot displayed;
    private boolean active, heldExpanded;
    private long previewStart = System.nanoTime();
    public float center, top, scale = 1;

    public PortableEngine(PortableSettings config) { this.config = config; }
    public boolean active() { return active; }
    public void tick(boolean inWorld) {
        boolean wanted = config.enabled && (inWorld || config.showInMenus);
        if (wanted && !active) { active = true; provider = new WindowsMusicProvider(); }
        else if (!wanted && active) close();
    }
    public void expand() { if (active) presentation.expand(System.nanoTime()); }
    public void dismiss() { gesture.cancel(); gesture.clearPending(); heldExpanded = false; presentation.dismiss(); }
    public void cancelGesture() { gesture.cancel(); heldExpanded = false; }
    public void restartPreview() { preview.reset(); previewStart = System.nanoTime(); config.preview = true; config.save(); }
    public BufferedImage draw(int width, int height, boolean menu) {
        if (!active) return null;
        long now = System.nanoTime();
        provider.preference(config.sourceName());
        MediaSnapshot latest = config.preview ? preview.get((now-previewStart)*1e-9, now) : provider.snapshot();
        gesture.reconcile(latest);
        presentation.update(latest,now,config.announcement,config.collapse,config.paused,config.motionName(),config.preview,
                gesture.owner!=MusicGesture.Owner.NONE,!provider.error().isEmpty(),gesture.pressedControl(),provider.error());
        if (latest!=null) displayed=latest; else if(presentation.state==MusicPresentation.State.HIDDEN) displayed=null;
        scale=(float)Math.max(.3,Math.min(config.scale,Math.min((width-16)/MusicLayout.WIDTH,(height-60)/MusicLayout.HEIGHT)));
        center=Math.max(81*scale+6,Math.min(width-81*scale-6,width*.5f+config.horizontalOffset));
        top=Math.max(4,Math.min(config.offset,height-MusicLayout.HEIGHT*scale-6));
        if(menu){center=width*.5f;top=Math.max(4,height-MusicLayout.HEIGHT*scale-28);}
        double elapsed=gesture.elapsed(displayed,now,provider.pending(),provider.error());
        String status=config.preview?"Preview · controls are simulated":provider.error();
        if(latest==null&&status.isEmpty())status="Start playback in a media app";
        return canvas.draw(presentation,displayed,elapsed,now,config.preview?MusicAudio.SILENT:provider.audio(),config,status,gesture.pressedControl());
    }
    public boolean hit(double x,double y) {
        return active&&presentation.visibility.getCurrentValue()>.1&&Math.abs(x-center)<=presentation.width.getCurrentValue()*scale*.5
                &&y>=top&&y<=top+presentation.height.getCurrentValue()*scale;
    }
    public boolean press(double x,double y,int button) {
        if(button!=0||!hit(x,y))return false;
        MediaSnapshot s=displayed;
        if(s==null){expand();return true;}
        float px=(float)((x-center)/scale+81),py=(float)((y-top)/scale);
        MusicGesture.Owner owner=MusicGesture.Owner.EXPAND;
        if(presentation.reveal.getCurrentValue()>.9){
            if(py>=MusicLayout.SEEK_Y-6&&py<=MusicLayout.SEEK_Y+8&&px>=MusicLayout.SEEK_X-6&&px<=MusicLayout.SEEK_X+MusicLayout.SEEK_WIDTH+8&&s.seek)owner=MusicGesture.Owner.SEEK;
            else if(py>=MusicLayout.CONTROL_Y-10&&py<=MusicLayout.CONTROL_Y+10){
                if(Math.abs(px-81)<10){if(!(presentation.controls.playing(s)?s.pause:s.play))return true;owner=MusicGesture.Owner.PLAY;}
                else if(Math.abs(px-(81-MusicLayout.CONTROL_SPACING))<10){if(!s.previous)return true;owner=MusicGesture.Owner.PREVIOUS;}
                else if(Math.abs(px-(81+MusicLayout.CONTROL_SPACING))<10){if(!s.next)return true;owner=MusicGesture.Owner.NEXT;}
            }else if(px>=MusicLayout.ART_X&&px<=MusicLayout.ART_X+MusicLayout.ART_SIZE&&py>=MusicLayout.ART_Y&&py<=MusicLayout.ART_Y+MusicLayout.ART_SIZE&&s.open)owner=MusicGesture.Owner.OPEN;
        }
        gesture.begin(owner,s,System.nanoTime());if(owner==MusicGesture.Owner.SEEK)drag(x,y);return true;
    }
    public void drag(double x,double y) {
        if(gesture.owner==MusicGesture.Owner.SEEK)gesture.drag(((x-center)/scale+81-MusicLayout.SEEK_X)/MusicLayout.SEEK_WIDTH);
        if(gesture.owner==MusicGesture.Owner.EXPAND&&System.nanoTime()-gesture.pressedAt>250000000L&&!heldExpanded&&hit(x,y)){expand();heldExpanded=true;}
    }
    public boolean ownsPointer(){return gesture.owner!=MusicGesture.Owner.NONE;}
    public void release(double x,double y,int button) {
        if(button!=0||!ownsPointer())return;
        if(gesture.owner==MusicGesture.Owner.SEEK)drag(x,y);
        MusicGesture.Owner owner=gesture.owner;MediaSnapshot target=gesture.target();
        boolean valid=gesture.valid(displayed)&&hit(x,y);
        float px=(float)((x-center)/scale+81),py=(float)((y-top)/scale);
        if(owner==MusicGesture.Owner.SEEK)valid&=py>=MusicLayout.SEEK_Y-8&&py<=MusicLayout.SEEK_Y+10;
        else if(owner!=MusicGesture.Owner.EXPAND){float c=81+(owner==MusicGesture.Owner.PREVIOUS?-MusicLayout.CONTROL_SPACING:owner==MusicGesture.Owner.NEXT?MusicLayout.CONTROL_SPACING:0);
            valid&=owner==MusicGesture.Owner.OPEN?py<42:py>=MusicLayout.CONTROL_Y-10&&py<=MusicLayout.CONTROL_Y+10&&Math.abs(px-c)<10;}
        double value=gesture.candidate;
        if(valid&&owner==MusicGesture.Owner.SEEK&&!config.preview)gesture.retainSeek(System.nanoTime());
        gesture.cancel();
        if(!valid){if(heldExpanded)presentation.dismiss();heldExpanded=false;return;}
        if(owner==MusicGesture.Owner.EXPAND){expand();presentation.acknowledge();}
        else send(owner==MusicGesture.Owner.PLAY?(presentation.controls.playing(target)?"pause":"play"):owner.name().toLowerCase(java.util.Locale.ROOT),target,value);
        heldExpanded=false;
    }
    /** Action names keep key codes and input libraries out of the common engine. */
    public void action(String op) {
        if(!active)return;
        MediaSnapshot target=config.preview?displayed:provider.snapshot();if(target==null)return;
        if("toggle".equals(op))op=presentation.controls.playing(target)?"pause":"play";
        if(("pause".equals(op)&&!target.pause)||("play".equals(op)&&!target.play)||("previous".equals(op)&&!target.previous)||("next".equals(op)&&!target.next))return;
        send(op,target,0);
    }
    private void send(String op,MediaSnapshot target,double value) {
        long now=System.nanoTime();boolean accepted;
        if(config.preview){preview.command(op,target,value,now);accepted=true;}else accepted=provider!=null&&provider.command(op,target,value);
        if(!accepted&&"seek".equals(op))gesture.clearPending();
        presentation.controls.request(op,target,now,accepted);
        if(accepted&&("play".equals(op)||"pause".equals(op)))presentation.playback.setTarget("play".equals(op)?1:0);
    }
    @Override public void close() {
        active=false;WindowsMusicProvider old=provider;provider=null;if(old!=null)old.close();
        dismiss();presentation.controls.reset();displayed=null;
    }
}
