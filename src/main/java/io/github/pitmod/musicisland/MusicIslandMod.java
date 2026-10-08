package io.github.pitmod.musicisland;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid="musicisland", name="MusicIsland", version="1.1.2", acceptedMinecraftVersions="[1.8.9]",
        clientSideOnly=true, guiFactory="io.github.pitmod.musicisland.MusicIslandGuiFactory")
public final class MusicIslandMod {
    public static MusicIslandConfig config;
    public static MusicIsland island;

    @Mod.EventHandler public void init(FMLInitializationEvent event){
        config=new MusicIslandConfig();
        island=new MusicIsland(config);
        MinecraftForge.EVENT_BUS.register(island);
        FMLCommonHandler.instance().bus().register(island);
        ClientCommandHandler.instance.registerCommand(new CommandBase(){
            @Override public String getCommandName(){return "musicisland";}
            @Override public String getCommandUsage(ICommandSender sender){return "/musicisland [controls]";}
            @Override public int getRequiredPermissionLevel(){return 0;}
            @Override public void processCommand(ICommandSender sender,String[] args){
                if(args.length>0&&"controls".equalsIgnoreCase(args[0]))island.open();else config.openGui();
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(()->island.closeProvider(),"MusicIsland shutdown"));
    }
}
