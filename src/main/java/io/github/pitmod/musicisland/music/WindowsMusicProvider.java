package io.github.pitmod.musicisland.music;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** One worker owns pipe I/O and image decoding. Render thread reads immutable snapshots only. */
public final class WindowsMusicProvider implements AutoCloseable {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"MusicIsland sessions");t.setDaemon(true);return t;});
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"MusicIsland watchdog");t.setDaemon(true);return t;});
    private final Map<String,BufferedImage> artCache=new LinkedHashMap<String,BufferedImage>(8,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,BufferedImage> e){return size()>8;}};
    private volatile MediaSnapshot snapshot;
    private volatile MusicAudio audio=MusicAudio.SILENT;
    private long nextMetadata;
    private volatile String error="",preference="Playing";
    private volatile Process process;
    private BufferedReader input;private BufferedWriter output;private long id,retryAt,controlErrorUntil;private int failures;
    private volatile boolean closed;private volatile boolean commandPending;
    public WindowsMusicProvider(){worker.scheduleWithFixedDelay(this::poll,0,25,TimeUnit.MILLISECONDS);}
    public MediaSnapshot snapshot(){return snapshot;}
    public MusicAudio audio(){return audio;}
    public String error(){return error;}
    public boolean pending(){return commandPending;}
    public void preference(String p){preference=p;}
    private void poll(){if(closed||System.nanoTime()<retryAt)return;try{
        if(System.nanoTime()<nextMetadata){readAudio();return;}
        nextMetadata=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(400);
        JsonObject request=new JsonObject();request.addProperty("op","poll");request.addProperty("preference",preference);
        request.addProperty("knownArt",snapshot==null||snapshot.artwork==null?"":snapshot.artHash);
        JsonObject data=exchange(request).getAsJsonObject("data");
        if(!data.get("available").getAsBoolean()){snapshot=null;audio=MusicAudio.SILENT;error="";return;}
        String hash=data.get("artHash").getAsString(),encoded=data.get("art").getAsString();
        BufferedImage image=artCache.get(hash);
        if(image==null&&!encoded.isEmpty()){
            byte[] bytes=Base64.getDecoder().decode(encoded);
            // Read dimensions before allocating decompressed image memory.
            try(javax.imageio.stream.ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                Iterator<javax.imageio.ImageReader> readers=ImageIO.getImageReaders(stream);
                if(readers.hasNext()){javax.imageio.ImageReader reader=readers.next();try{reader.setInput(stream);
                    int w=reader.getWidth(0),h=reader.getHeight(0);if(w>0&&h>0&&w<=2048&&h<=2048){image=reader.read(0);if(image!=null)artCache.put(hash,image);}
                }finally{reader.dispose();}}
            }
        }
        MediaSnapshot decoded=MediaSnapshot.decode(data,System.nanoTime(),image);
        synchronized(this){if(closed)return;snapshot=decoded;if(System.nanoTime()>controlErrorUntil)error="";failures=0;}
        readAudio();
    }catch(Exception e){fail(e);}}
    private void readAudio()throws Exception{
        JsonObject request=new JsonObject();request.addProperty("op","meter");JsonObject data=exchange(request).getAsJsonObject("data");
        boolean available=data.get("available").getAsBoolean();String source=data.get("source").getAsString();float peak=data.get("peak").getAsFloat();
        synchronized(this){if(!closed)audio=audio.append(available,source,peak,System.nanoTime(),snapshot!=null&&snapshot.playing);}
    }
    public synchronized boolean command(String op,MediaSnapshot target,double seconds){
        if(closed||target==null||commandPending)return false;
        commandPending=true;error="";
        worker.execute(()->{try{
            // A queued command must still refer to the presented track when execution starts.
            if(!target.sameTrack(snapshot)){controlError("Track changed; command cancelled");return;}
            JsonObject request=new JsonObject();request.addProperty("op",op);request.addProperty("session",target.session);request.addProperty("track",target.track);request.addProperty("seconds",Math.max(0,Math.min(target.duration,seconds)));
            JsonObject result=exchange(request).getAsJsonObject("data");
            if(!result.get("accepted").getAsBoolean())controlError(result.get("reason").getAsString());
            else error="";
        }catch(Exception e){fail(e);}finally{commandPending=false;nextMetadata=0;poll();}});return true;
    }
    private void controlError(String message){error=message;controlErrorUntil=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);}
    private JsonObject exchange(JsonObject request)throws Exception {
        if(closed)throw new IOException("Closed");if(process==null||!process.isAlive())start();
        Process owner=process;long requestId=++id;request.addProperty("id",requestId);
        ScheduledFuture<?> timeout=watchdog.schedule(()->{if(owner.isAlive())owner.destroyForcibly();},8,TimeUnit.SECONDS);
        try{
            output.write(request.toString());output.newLine();output.flush();
            String line=boundedLine(input,1500000);if(line==null)throw new IOException("Bridge stopped");
            JsonObject response=new JsonParser().parse(line).getAsJsonObject();
            if(response.get("id").getAsLong()!=requestId)throw new IOException("Unexpected reply");
            if(!response.get("ok").getAsBoolean())throw new IOException(response.get("error").getAsString());return response;
        }finally{timeout.cancel(false);}
    }
    private static String boundedLine(Reader reader,int max)throws IOException{StringBuilder b=new StringBuilder();int c;while((c=reader.read())!=-1){if(c=='\n')return b.toString();if(c!='\r')b.append((char)c);if(b.length()>max)throw new IOException("Oversized bridge reply");}return b.length()==0?null:b.toString();}
    private void start()throws Exception {
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("windows"))throw new IOException("Windows media sessions require Windows 10 or later");
        byte[] executable;try(InputStream resource=getClass().getResourceAsStream("/assets/musicisland/music/MusicSessionBridge.exe")){
            if(resource==null)throw new IOException("Music helper missing");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;while((n=resource.read(b))!=-1)out.write(b,0,n);executable=out.toByteArray();}
        StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(executable))hash.append(String.format("%02x",b&255));
        Path dir=Paths.get(System.getProperty("java.io.tmpdir"),"musicisland-bridge",hash.toString());Files.createDirectories(dir);
        Path exe=dir.resolve("MusicSessionBridge.exe");
        if(!Files.exists(exe)||!MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(exe)),MessageDigest.getInstance("SHA-256").digest(executable))){
            Path temp=Files.createTempFile(dir,"bridge-",".tmp");try{Files.write(temp,executable);Files.move(temp,exe,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
        }
        Process fresh=new ProcessBuilder(exe.toString()).start();process=fresh;
        input=new BufferedReader(new InputStreamReader(fresh.getInputStream(),StandardCharsets.UTF_8));output=new BufferedWriter(new OutputStreamWriter(fresh.getOutputStream(),StandardCharsets.UTF_8));
        Thread drain=new Thread(()->{try(InputStream in=fresh.getErrorStream()){byte[] discard=new byte[1024];while(in.read(discard)!=-1){}}catch(IOException ignored){}},"MusicIsland stderr");drain.setDaemon(true);drain.start();
        if(closed){fresh.destroyForcibly();throw new IOException("Closed");}
    }
    private void fail(Exception e){snapshot=null;audio=MusicAudio.SILENT;error="Media session unavailable: "+e.getMessage();Process p=process;process=null;if(p!=null)p.destroyForcibly();retryAt=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.min(30,1L<<Math.min(5,failures++)));}
    public synchronized void close(){closed=true;snapshot=null;audio=MusicAudio.SILENT;Process p=process;if(p!=null)p.destroyForcibly();worker.shutdownNow();watchdog.shutdownNow();}
}
