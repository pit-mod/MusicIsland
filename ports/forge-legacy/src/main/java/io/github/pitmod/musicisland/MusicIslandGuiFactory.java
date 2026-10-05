package io.github.pitmod.musicisland;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import java.util.Set;
public final class MusicIslandGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft mc){}
    @Override public Class<? extends GuiScreen> mainConfigGuiClass(){return ConfigScreen.class;}
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories(){return null;}
    @Override public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element){return null;}
    public static final class ConfigScreen extends MusicIslandConfig.SettingsScreen { public ConfigScreen(GuiScreen parent){super(parent);} }
}
