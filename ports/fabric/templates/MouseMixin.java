package io.github.pitmod.musicisland.fabric.mixin;
import io.github.pitmod.musicisland.fabric.MusicIslandClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Routes HUD gestures before vanilla dispatch, without modifying widget input. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow public abstract double getX();
    @Shadow public abstract double getY();
    private double islandX(Screen s){return getX()*s.width/MinecraftClient.getInstance().getWindow().getWidth();}
    private double islandY(Screen s){return getY()*s.height/MinecraftClient.getInstance().getWindow().getHeight();}
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void button(long window,int button,int action,int modifiers,CallbackInfo ci){
        Screen s=MinecraftClient.getInstance().currentScreen;if(s==null||MusicIslandClient.INSTANCE==null)return;
        if(action==1?MusicIslandClient.INSTANCE.clickHook(islandX(s),islandY(s),button):action==0&&MusicIslandClient.INSTANCE.releaseHook(islandX(s),islandY(s),button))ci.cancel();
    }
    @Inject(method="onCursorPos",at=@At("TAIL"))
    private void move(long window,double x,double y,CallbackInfo ci){Screen s=MinecraftClient.getInstance().currentScreen;if(s!=null&&MusicIslandClient.INSTANCE!=null)MusicIslandClient.INSTANCE.dragHook(islandX(s),islandY(s));}
}
