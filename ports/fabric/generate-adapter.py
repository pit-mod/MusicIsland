"""Apply explicit Minecraft API differences to the canonical Fabric adapter."""
from pathlib import Path
import sys

version, destination = sys.argv[1:]
minor = int(version.split('.')[1])
patch = int(version.split('.')[2]) if len(version.split('.')) > 2 else 0
source = Path('src/main/java/io/github/pitmod/musicisland/fabric/MusicIslandClient.java').read_text(encoding='utf-8')
if minor >= 21:
    source = source.replace('new Identifier("musicisland","hud")', 'Identifier.of("musicisland","hud")')
if minor == 21 and patch >= 5:
    source = source.replace('new NativeImageBackedTexture(pixels)', 'new NativeImageBackedTexture(()->"MusicIsland HUD",pixels)')
    source = source.replace('pixels.setColor(x,y,toABGR(data[y*image.getWidth()+x]))', 'pixels.setColorArgb(x,y,data[y*image.getWidth()+x])')
if minor == 21 and patch >= 6:
    source = source.replace('graphics.getMatrices().push()', 'graphics.getMatrices().pushMatrix()').replace('graphics.getMatrices().pop()', 'graphics.getMatrices().popMatrix()')
    source = source.replace('graphics.getMatrices().translate(engine.center-PortableCanvas.WIDTH*engine.scale*.5f,engine.top-12*engine.scale,0)', 'graphics.getMatrices().translate(engine.center-PortableCanvas.WIDTH*engine.scale*.5f,engine.top-12*engine.scale)')
    source = source.replace('graphics.getMatrices().scale(engine.scale,engine.scale,1)', 'graphics.getMatrices().scale(engine.scale,engine.scale)')
    old = 'graphics.drawTexture(TEXTURE,0,0,PortableCanvas.WIDTH,PortableCanvas.HEIGHT,0,0,PortableCanvas.WIDTH*PortableCanvas.DENSITY,PortableCanvas.HEIGHT*PortableCanvas.DENSITY,PortableCanvas.WIDTH*PortableCanvas.DENSITY,PortableCanvas.HEIGHT*PortableCanvas.DENSITY)'
    source = source.replace(old, 'graphics.drawTexturedQuad(TEXTURE,0,0,PortableCanvas.WIDTH,PortableCanvas.HEIGHT,0,1,0,1)')
if minor == 21 and patch >= 9:
    source = source.replace('import org.lwjgl.glfw.GLFW;', 'import org.lwjgl.glfw.GLFW;\nimport net.minecraft.client.gui.Click;\nimport net.minecraft.client.input.KeyInput;')
    source = source.replace('String category="category.musicisland";', 'KeyBinding.Category category=KeyBinding.Category.create(Identifier.of("musicisland","controls"));')
    source = source.replace('String constant,String category)', 'String constant,KeyBinding.Category category)')
    source = source.replace('(s,x,y,button)->!engine.press(x,y,button)', '(s,e)->!engine.press(e.x(),e.y(),e.button())')
    source = source.replace('(s,x,y,button)->{\n                if(!engine.ownsPointer())return true;engine.release(x,y,button);return false;', '(s,e)->{\n                if(!engine.ownsPointer())return true;engine.release(e.x(),e.y(),e.button());return false;')
    source = source.replace('mouseClicked(double x,double y,int button){', 'mouseClicked(Click e,boolean twice){double x=e.x(),y=e.y();int button=e.button();')
    source = source.replace('mouseReleased(double x,double y,int button){', 'mouseReleased(Click e){double x=e.x(),y=e.y();int button=e.button();')
    source = source.replace('mouseDragged(double x,double y,int button,double dx,double dy){', 'mouseDragged(Click e,double dx,double dy){double x=e.x(),y=e.y();int button=e.button();')
    source = source.replace('keyPressed(int key,int scancode,int modifiers)', 'keyPressed(KeyInput e)').replace('.matchesKey(key,scancode)', '.matchesKey(e)').replace('super.keyPressed(key,scancode,modifiers)', 'super.keyPressed(e)')
output = Path(destination) / 'io/github/pitmod/musicisland/fabric/MusicIslandClient.java'
output.parent.mkdir(parents=True, exist_ok=True)
for name in ('ScreenMixin','MouseMixin','HudMixin'):
    (output.parent/(name+'.java')).unlink(missing_ok=True)
mixin_dir=output.parent/'mixin'
mixin_dir.mkdir(exist_ok=True)
if minor < 20:
    source = source.replace('import net.minecraft.client.gui.DrawContext;', 'import net.minecraft.client.util.math.MatrixStack;')
    source = source.replace('DrawContext', 'LegacyDrawContext')
    source = source.replace('draw(graphics,false);', 'draw(new LegacyDrawContext(graphics),false);')
    source = source.replace('draw(g,true)', 'draw(new LegacyDrawContext(g),true)')
    source = source.replace('render(LegacyDrawContext g,int x,int y,float delta){', 'render(MatrixStack matrices,int x,int y,float delta){LegacyDrawContext g=new LegacyDrawContext(matrices);')
    source = source.replace('super.render(g,x,y,delta)', 'super.render(matrices,x,y,delta)')
    # Client command v2 arrived in 1.19; v1 has a client-local dispatcher.
    if minor < 19 and minor >= 17:
        source = source.replace('import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;', 'import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;')
        source = source.replace('net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource', 'net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource')
        source = source.replace('ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->dispatcher.register(', 'ClientCommandManager.DISPATCHER.register(')
        source = source.replace('showControls();return 1;}))));', 'showControls();return 1;})));')
    if minor < 19:
        source = source.replace('Text.literal(', 'new net.minecraft.text.LiteralText(')
        source = source.replace('addDrawableChild(', 'addButton(') if minor < 17 else source
        source = source.replace('clearAndInit()', 'rebuildSettings()')
        source = source.replace('        @Override protected void init(){', '        private void rebuildSettings(){clearChildren();init();}\n        @Override protected void init(){')
        import re
        source = re.sub(r'ButtonWidget.builder\((.*?)\).dimensions\((.*?)\).build\(\)',lambda m:'new ButtonWidget('+m[2]+','+m[1]+')',source)
    if minor < 17:
        source = source.replace('import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;\n','').replace('import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;\n','')
        source = source.replace('import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;\n','').replace('import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;\n','')
        start=source.index('        ScreenEvents.AFTER_INIT.register(')
        end=source.index('        Runtime.getRuntime()',start)
        source=source[:start]+source[end:]
        source=source.replace('    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name){return LiteralArgumentBuilder.literal(name);}\n','')
        source=source.replace('public final class MusicIslandClient implements ClientModInitializer {','public final class MusicIslandClient implements ClientModInitializer {\n    public static MusicIslandClient INSTANCE;')
        source=source.replace('@Override public void onInitializeClient() {','@Override public void onInitializeClient() {\n        INSTANCE=this;')
        source=source.replace('private void rebuildSettings(){clearChildren();init();}', 'private void rebuildSettings(){buttons.clear();children.clear();init();}')
        hooks='''
    public void drawScreenHook(LegacyDrawContext g){Screen s=currentScreen();if(!(s instanceof IslandScreen)&&!(s instanceof SettingsScreen))draw(g,true);}
    public boolean clickHook(double x,double y,int button){return engine.press(x,y,button);}
    public boolean releaseHook(double x,double y,int button){if(!engine.ownsPointer())return false;engine.release(x,y,button);return true;}
    public boolean dragHook(double x,double y){if(!engine.ownsPointer())return false;engine.drag(x,y);return true;}
    public boolean commandHook(String text){if(text.equalsIgnoreCase("/musicisland")){showSettings();return true;}if(text.equalsIgnoreCase("/musicisland controls")){showControls();return true;}return false;}
'''
        source=source.replace('    private void showControls()',hooks+'    private void showControls()')
        mixin=Path('templates/ScreenMixin.java').read_text(encoding='utf-8')
        if minor < 16:
            source=source.replace('import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;\n','').replace('import net.minecraft.client.util.math.MatrixStack;\n','')
            start=source.index('        HudRenderCallback.EVENT.register(');end=source.index('        Runtime.getRuntime()',start)
            source=source[:start]+source[end:]
            source=source.replace('render(MatrixStack matrices,int x,int y,float delta){LegacyDrawContext g=new LegacyDrawContext(matrices);','render(int x,int y,float delta){LegacyDrawContext g=new LegacyDrawContext();')
            source=source.replace('super.render(matrices,x,y,delta)','super.render(x,y,delta)')
            source=source.replace('    public void drawScreenHook(', '    public void drawHudHook(){if(currentScreen()==null)draw(new LegacyDrawContext(),false);}\n    public void drawScreenHook(')
            mixin=mixin.replace('import net.minecraft.client.util.math.MatrixStack;\n','').replace('render(MatrixStack matrices,int x,int y,float delta,CallbackInfo ci)','render(int x,int y,float delta,CallbackInfo ci)').replace('new LegacyDrawContext(matrices)','new LegacyDrawContext()')
            (mixin_dir/'HudMixin.java').write_text(Path('templates/HudMixin.java').read_text(encoding='utf-8'),encoding='utf-8')
        (mixin_dir/'ScreenMixin.java').write_text(mixin,encoding='utf-8')
        mouse=Path('templates/MouseMixin.java').read_text(encoding='utf-8')
        if minor==14:mouse=mouse.replace('.getWindow()', '.window')
        (mixin_dir/'MouseMixin.java').write_text(mouse,encoding='utf-8')
        import json
        resources=Path(destination).parent/'resources';resources.mkdir(parents=True,exist_ok=True)
        (resources/'musicisland.mixins.json').write_text(json.dumps({'required':True,'package':'io.github.pitmod.musicisland.fabric.mixin','compatibilityLevel':'JAVA_8','refmap':'musicisland.refmap.json','client':['ScreenMixin','MouseMixin']+(['HudMixin'] if minor<16 else []),'injectors':{'defaultRequire':1}},indent=2))
    if minor == 17:
        source=source.replace('shouldPause()', 'isPauseScreen()').replace('close()', 'onClose()')
    if minor == 16:
        source=source.replace('mc.setScreen(s)','mc.openScreen(s)').replace('pixels.setColor(', 'pixels.setPixelColor(')
        source=source.replace('shouldPause()', 'isPauseScreen()').replace('close()', 'onClose()')
    if minor < 16:
        source=source.replace('net.minecraft.client.option.KeyBinding','net.minecraft.client.options.KeyBinding').replace('mc.setScreen(s)','mc.openScreen(s)').replace('pixels.setColor(', 'pixels.setPixelRgba(')
        source=source.replace('shouldPause()', 'isPauseScreen()').replace('close()', 'onClose()').replace('textRenderer,','font,')
        source=re.sub(r'new net.minecraft.text.LiteralText\((.*?)\)(?=,b->)',lambda m:m[1],source)
    helper = Path('templates/LegacyDrawContext.java').read_text(encoding='utf-8')
    if minor < 17:
        helper = helper.replace('RenderSystem.setShader(GameRenderer::getPositionTexProgram);RenderSystem.setShaderTexture(0,id);', 'MinecraftClient.getInstance().getTextureManager().bindTexture(id);')
    if minor < 16:
        helper=Path('templates/LegacyDrawContext14.java').read_text(encoding='utf-8')
        helper=helper.replace('font.getWidth(', 'font.getStringWidth(').replace('DrawableHelper.drawTexture(', 'DrawableHelper.blit(')
        if minor == 14:helper=helper.replace('.getWindow()', '.window')
    elif minor < 19:
        helper=helper.replace('getPositionTexProgram','getPositionTexShader')
    (output.parent/'LegacyDrawContext.java').write_text(helper,encoding='utf-8')
output.write_text(source, encoding='utf-8')
