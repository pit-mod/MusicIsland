package io.github.pitmod.musicisland.portable;

/** Shared, paginated settings definitions, rendered with native widgets by each loader. */
public final class SettingsRows {
    public static final int COUNT=14;
    private SettingsRows() {}
    public static String label(PortableSettings s,int i) {
        switch(i){
            case 0:return "Enabled: "+on(s.enabled);
            case 1:return "Show in Minecraft menus: "+on(s.showInMenus);
            case 2:return "Scale: "+String.format(java.util.Locale.ROOT,"%.1f",s.scale);
            case 3:return "Top offset: "+s.offset;
            case 4:return "Horizontal offset: "+s.horizontalOffset;
            case 5:return "Compact title: "+on(s.compactTitle);
            case 6:return "Visualizer: "+on(s.indicator);
            case 7:return "Motion: "+s.motionName();
            case 8:return "Media source: "+s.sourceName();
            case 9:return "Auto-collapse: "+(s.collapse==0?"Off":s.collapse+"s");
            case 10:return "Paused visibility: "+(s.paused==0?"Always":s.paused+"s");
            case 11:return "Arrow controls: "+on(s.arrows);
            case 12:return "Preview: "+on(s.preview);
            default:return "Track announcement: "+s.announcement+"s";
        }
    }
    public static void change(PortableSettings s,int i,int direction){
        switch(i){
            case 0:s.enabled=!s.enabled;break;case 1:s.showInMenus=!s.showInMenus;break;
            case 2:s.scale=cycle(s.scale,direction*.1f,.6f,2.5f);break;
            case 3:s.offset=(int)cycle(s.offset,direction*4,0,200);break;
            case 4:s.horizontalOffset=(int)cycle(s.horizontalOffset,direction*10,-600,600);break;
            case 5:s.compactTitle=!s.compactTitle;break;case 6:s.indicator=!s.indicator;break;
            case 7:s.motion=(s.motion+direction+3)%3;break;case 8:s.source=(s.source+direction+3)%3;break;
            case 9:s.collapse=(int)cycle(s.collapse,direction*5,0,120);break;
            case 10:s.paused=(int)cycle(s.paused,direction*5,0,120);break;
            case 11:s.arrows=!s.arrows;break;case 12:s.preview=!s.preview;break;
            case 13:s.announcement=cycle(s.announcement,direction*.5f,0,10);break;
        }s.save();
    }
    private static float cycle(float v,float d,float min,float max){float next=v+d;return next>max+.001f?min:next<min-.001f?max:Math.max(min,Math.min(max,next));}
    private static String on(boolean value){return value?"On":"Off";}
}
