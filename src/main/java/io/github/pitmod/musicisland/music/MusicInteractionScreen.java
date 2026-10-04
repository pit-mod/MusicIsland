package io.github.pitmod.musicisland.music;

import io.github.pitmod.musicisland.gui.GuiDraw;
import io.github.pitmod.musicisland.MusicIsland;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.io.IOException;

/** Minecraft owns cursor capture/restoration. No custom mouse helper or sensitivity changes. */
public final class MusicInteractionScreen extends GuiScreen {
    private final MusicIsland island;private final GuiScreen previous;
    public MusicInteractionScreen(MusicIsland island,GuiScreen previous){this.island=island;this.previous=previous;}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void drawScreen(int x,int y,float partial){if(!island.isToggled()){finish();return;}GuiDraw.resetState();island.drag(x,y);island.draw();
        if(!island.controlsOpen()&&!island.ownsPointer()){finish();return;}
        GuiDraw.textFitScaled(island.fullTitle(),12,height-27,.65f,width-24,0xFFB0B0B8,false);GuiDraw.textFitScaled("↓ open/close · ↑ play/pause · ← previous · → next · Escape return",12,height-15,.65f,width-24,0xFF82828A,false);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException {if(!island.press(x,y,button)&&button==0)finish();}
    @Override protected void mouseReleased(int x,int y,int button){island.release(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long time){island.drag(x,y);}
    @Override protected void keyTyped(char typed,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE||island.isInteractionKey(key))finish();else if(!Keyboard.isRepeatEvent())island.arrowKey(key);}
    @Override public void onGuiClosed(){island.dismiss();}
    public void finish(){island.dismiss();mc.displayGuiScreen(island.isToggled()?previous:null);}
}
