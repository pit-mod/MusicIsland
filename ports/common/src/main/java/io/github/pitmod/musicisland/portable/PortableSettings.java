package io.github.pitmod.musicisland.portable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Loader-independent settings used where OneConfig has no compatible release. */
public class PortableSettings {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled=true,showInMenus=false,compactTitle=true,indicator=true,arrows=true,preview=false;
    public float scale=1,announcement=2;
    public int offset=8,horizontalOffset=0,motion=0,source=0,collapse=0,paused=15;
    public transient Path file;
    public String motionName(){return new String[]{"Fluid","Reduced","Off"}[Math.max(0,Math.min(2,motion))];}
    public String sourceName(){return new String[]{"Playing","Spotify","Current"}[Math.max(0,Math.min(2,source))];}
    public static PortableSettings load(Path file){
        PortableSettings settings=null;
        if(Files.isRegularFile(file))try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            settings=JSON.fromJson(reader,PortableSettings.class);
        }catch(Exception e){System.err.println("[MusicIsland] Cannot read settings: "+e.getMessage());}
        if(settings==null)settings=new PortableSettings();
        settings.file=file;settings.clamp();return settings;
    }
    public void clamp(){
        if(!Float.isFinite(scale))scale=1;scale=Math.max(.6f,Math.min(2.5f,scale));
        offset=Math.max(0,Math.min(200,offset));horizontalOffset=Math.max(-600,Math.min(600,horizontalOffset));
        motion=Math.max(0,Math.min(2,motion));source=Math.max(0,Math.min(2,source));
        collapse=Math.max(0,Math.min(120,collapse));paused=Math.max(0,Math.min(120,paused));
        if(!Float.isFinite(announcement))announcement=2;announcement=Math.max(0,Math.min(10,announcement));
    }
    public void save(){
        if(file==null)return;clamp();
        try{
            Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"musicisland-",".tmp");
            try{
                try(Writer writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)){JSON.toJson(this,writer);}
                try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temp);}
        }catch(IOException e){System.err.println("[MusicIsland] Cannot save settings: "+e.getMessage());}
    }
}
