package io.github.pitmod.musicisland;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import java.util.Set;

/** Forge 1.12 introduced the direct configuration-screen factory contract. */
public final class MusicIslandGuiFactory implements IModGuiFactory {
    @Override public void initialize(Minecraft minecraft) {}
    @Override public boolean hasConfigGui() { return true; }
    @Override public GuiScreen createConfigGui(GuiScreen parent) { return new ConfigScreen(parent); }
    @Override public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() { return null; }
    public static final class ConfigScreen extends GuiScreen {
        private final GuiScreen parent;
        public ConfigScreen(GuiScreen parent) { this.parent=parent; }
        @Override public void initGui() { MusicIslandMod.config.openGui(); }
        @Override public boolean doesGuiPauseGame() { return false; }
    }
}
