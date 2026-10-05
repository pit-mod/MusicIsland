package io.github.pitmod.musicisland.portable;
import io.github.pitmod.musicisland.music.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Focused regressions for behavior that must stay consistent across all adapters. */
public final class CompatibilityChecks {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        long now=1_000_000_000L;MediaSnapshot first=MusicPreview.fixed(now);
        MusicPresentation model=new MusicPresentation();
        for(int i=0;i<120;i++)model.update(first,now+i*16_666_667L,2,0,15,"Fluid",false,false,false);
        require(!model.explicit&&model.height.getCurrentValue()<17,"Track arrival must stay compact");
        model.expand(now+2_000_000_000L);float previous=model.width.getCurrentValue();
        model.update(first,now+2_016_666_667L,2,0,15,"Fluid",false,false,false);
        require(model.width.getCurrentValue()>previous&&model.width.getCurrentValue()<MusicLayout.WIDTH,"Expansion must animate");
        model.dismiss();float reversal=model.width.getCurrentValue();
        model.update(first,now+2_033_333_334L,2,0,15,"Fluid",false,false,false);
        require(Math.abs(model.width.getCurrentValue()-reversal)<10,"Reversal must not snap");
        MediaSnapshot next=MusicPreview.at(8,now);
        model.update(next,now+2_050_000_001L,2,0,15,"Fluid",false,false,false);
        require(!model.explicit,"Changing tracks must not expand the island");

        MusicGesture gesture=new MusicGesture();require(gesture.begin(MusicGesture.Owner.SEEK,first,now),"Seek should begin");
        gesture.drag(.65);require(Math.abs(gesture.candidate-first.duration*.65)<.001,"Seek fraction should match duration");
        require(gesture.pressedControl()==4,"Seeking must own only the timeline animation");
        gesture.reconcile(next);require(gesture.owner==MusicGesture.Owner.NONE,"A new track must cancel a stale gesture");

        MusicControlMotion motion=new MusicControlMotion();motion.update(first,now,"Fluid",false,false,false,"");
        motion.request("pause",first,now+1,true);require(!motion.playing(first),"Pause feedback must start before the provider replies");
        motion.request("previous",first,now+2,true);require(motion.direction==-1,"Previous artwork must travel left");
        motion.request("next",first,now+3,true);require(motion.direction==1,"Next artwork must travel right");

        BufferedImage art=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<100;y++)for(int x=0;x<100;x++)art.setRGB(x,y,x<50?0xFFFF2222:0xFF2244FF);
        int[] palette=ArtworkAccent.columns(art,12);
        require(((palette[0]>>16)&255)>(palette[0]&255),"Left palette should stay red");
        require((palette[11]&255)>((palette[11]>>16)&255),"Right palette should stay blue");

        PortableSettings settings=new PortableSettings();PortableCanvas canvas=new PortableCanvas();
        model.expand(now);model.update(first,now+3_000_000_000L,2,0,15,"Off",false,false,false);
        model.update(first,now+3_016_666_667L,2,0,15,"Off",false,false,false);
        BufferedImage frame=canvas.draw(model,first,100,now+3_016_666_667L,MusicAudio.SILENT,settings,"",0);
        require((frame.getRGB(frame.getWidth()/2,60*PortableCanvas.DENSITY)>>>24)>240,"Expanded surface should be opaque");
        require((frame.getRGB(0,0)>>>24)==0,"HUD outside the island should stay transparent");
        int colored=0;for(int y=0;y<frame.getHeight();y++)for(int x=0;x<frame.getWidth();x++)if((frame.getRGB(x,y)&0xFFFFFF)!=0)colored++;
        require(colored>2000,"Artwork, controls and text must actually draw");
        Path output=Paths.get(args[0]);Files.createDirectories(output);ImageIO.write(frame,"png",output.resolve("expanded.png").toFile());
        canvas.pixelScale(5);frame=canvas.draw(model,first,100,now+3_016_666_667L,MusicAudio.SILENT,settings,"",0);
        require(frame.getWidth()>=PortableCanvas.WIDTH*5,"High GUI scale must not stretch a low-resolution texture");
        ImageIO.write(frame,"png",output.resolve("expanded-high-density.png").toFile());
        java.lang.reflect.Method text=PortableCanvas.class.getDeclaredMethod("text",java.awt.Graphics2D.class,String.class,float.class,float.class,float.class,java.awt.Color.class);
        java.lang.reflect.Method label=PortableCanvas.class.getDeclaredMethod("label",java.awt.Graphics2D.class,String.class,float.class,float.class,float.class,float.class,java.awt.Color.class,long.class,MusicMarquee.class,boolean.class);
        text.setAccessible(true);label.setAccessible(true);
        BufferedImage reference=new BufferedImage(800,100,BufferedImage.TYPE_INT_ARGB),clipped=new BufferedImage(800,100,BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D rg=reference.createGraphics(),cg=clipped.createGraphics();
        try{rg.scale(4,4);cg.scale(4,4);text.invoke(null,rg,"Everything I Am · Kanye West gyp",2f,2f,.84f,java.awt.Color.WHITE);label.invoke(null,cg,"Everything I Am · Kanye West gyp",2f,2f,190f,.84f,java.awt.Color.WHITE,now,null,false);}finally{rg.dispose();cg.dispose();}
        require(java.util.Arrays.equals(reference.getRGB(0,0,800,100,null,0,800),clipped.getRGB(0,0,800,100,null,0,800)),"Title/artist clipping must preserve entire glyphs including descenders");
        canvas.pixelScale(1);
        model.dismiss();model.update(first,now+4_000_000_000L,2,0,15,"Off",false,false,false);
        ImageIO.write(canvas.draw(model,first,100,now+4_000_000_000L,MusicAudio.SILENT,settings,"",0),"png",output.resolve("compact.png").toFile());
        settings.file=output.resolve("settings.json");settings.scale=Float.NaN;settings.motion=99;settings.save();
        PortableSettings loaded=PortableSettings.load(settings.file);require(loaded.scale==1&&loaded.motion==2,"Malformed values should be bounded and saved");
        require(!loaded.showInMenus,"Menus should stay disabled by default");
        System.out.println("PASS: shared presentation, artwork direction, optimistic controls, seek ownership, RGBA rendering and configuration");
    }
}
