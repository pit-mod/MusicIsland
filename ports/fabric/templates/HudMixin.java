package io.github.pitmod.musicisland.fabric.mixin;
import io.github.pitmod.musicisland.fabric.MusicIslandClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** HUD hook for releases predating Fabric's HUD API. */
@Mixin(InGameHud.class)
public abstract class HudMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void render(float delta,CallbackInfo ci){MusicIslandClient.INSTANCE.drawHudHook();}
}
