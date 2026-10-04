package io.github.pitmod.musicisland.music;

import org.lwjgl.input.Keyboard;

/** Capability-aware keyboard mapping, shared by gameplay and the cursor screen. */
public final class MusicControls {
    private MusicControls(){}
    public static boolean isArrow(int code){return code==Keyboard.KEY_DOWN||code==Keyboard.KEY_UP||code==Keyboard.KEY_LEFT||code==Keyboard.KEY_RIGHT;}
    public static String action(int code,MediaSnapshot target){
        if(code==Keyboard.KEY_DOWN)return "open-controls";
        if(target==null)return null;
        if(code==Keyboard.KEY_RIGHT)return target.next?"next":null;
        if(code==Keyboard.KEY_LEFT)return target.previous?"previous":null;
        if(code==Keyboard.KEY_UP)return target.playing?(target.pause?"pause":null):(target.play?"play":null);
        return null;
    }
}
