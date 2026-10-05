package io.github.pitmod.musicisland.fabric.mixin;
import io.github.pitmod.musicisland.fabric.MusicIslandClient;
import io.github.pitmod.musicisland.fabric.LegacyDrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Screen hooks for releases predating Fabric's Screen API. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void render(MatrixStack matrices,int x,int y,float delta,CallbackInfo ci){MusicIslandClient.INSTANCE.drawScreenHook(new LegacyDrawContext(matrices));}
    @Inject(method="sendMessage(Ljava/lang/String;Z)V",at=@At("HEAD"),cancellable=true)
    private void command(String text,boolean history,CallbackInfo ci){if(MusicIslandClient.INSTANCE.commandHook(text))ci.cancel();}
}
