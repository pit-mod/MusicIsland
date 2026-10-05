package io.github.pitmod.musicisland.fabric;
import java.lang.reflect.*;
/** 26.3 moved GPU types to RenderPearl; resolve the cache once per texture,
 * preserving one binary without depending on either version's GPU type names. */
final class HudSampling {
    private HudSampling(){}
    @SuppressWarnings({"rawtypes","unchecked"}) static void linear(Object texture){
        try{
            Object cache=Class.forName("com.mojang.blaze3d.systems.RenderSystem").getMethod("getSamplerCache").invoke(null);
            Object sampler=null;
            for(Method m:cache.getClass().getMethods())if(m.getName().equals("getClampToEdge")&&m.getParameterCount()==1){
                Object linear=Enum.valueOf((Class)m.getParameterTypes()[0],"LINEAR");sampler=m.invoke(cache,linear);break;
            }
            if(sampler==null)throw new NoSuchMethodException("Linear clamp sampler");
            Class<?> type=texture.getClass();Field field=null;
            while(type!=null){try{field=type.getDeclaredField("sampler");break;}catch(NoSuchFieldException e){type=type.getSuperclass();}}
            if(field==null)throw new NoSuchFieldException("Texture sampler");
            field.setAccessible(true);field.set(texture,sampler);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot configure smooth MusicIsland texture",e);}
    }
}
