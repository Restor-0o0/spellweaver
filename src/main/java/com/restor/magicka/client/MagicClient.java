package com.restor.magicka.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.restor.magicka.Element;
import com.restor.magicka.Formula;
import com.restor.magicka.Magicka;
import com.restor.magicka.MagicNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class MagicClient {
    static {MagicNetwork.visualReceiver=SpellVisuals::receive;}
    @SubscribeEvent public static void visualsTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){SpellVisuals.tick();}
    @SubscribeEvent public static void visuals(net.neoforged.neoforge.client.event.RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(event.getStage()!=net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        SpellVisuals.render(pose,mc.renderBuffers().bufferSource(),camera,event.getPartialTick().getGameTimeDeltaPartialTick(false));pose.popPose();
    }
    // Elements stay under the resting left hand: 1-4 above WASD, Q E R F around it.
    private static final int[] ELEMENT_KEYS = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_F};
    private static final KeyMapping[] ELEMENTS = java.util.Arrays.stream(Element.values()).map(e ->
            key(e.name().toLowerCase(java.util.Locale.ROOT), InputConstants.Type.KEYSYM, ELEMENT_KEYS[e.ordinal()])).toArray(KeyMapping[]::new);
    private static final KeyMapping TOGGLE = key("toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G);
    private static final KeyMapping CLEAR = key("clear", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V);
    private static final KeyMapping DIRECTED = key("directed", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    private static final KeyMapping SELF = key("self", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
    private static final KeyMapping BOOK = key("spellbook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B);
    private static final KeyMapping INSTANT = key("instant", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H);
    private static final KeyMapping SHAPE = key("shape", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K);
    private static final KeyMapping[] QUICK=java.util.stream.IntStream.range(0,4).mapToObj(i->new KeyMapping("key.magicka.quick_"+(i+1),KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.ALT,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_1+i,"key.categories.magicka")).toArray(KeyMapping[]::new);
    public static Component quickKey(int index){return QUICK[index].getTranslatedKeyMessage();}
    public static Component elementKey(Element element) { return ELEMENTS[element.ordinal()].getTranslatedKeyMessage(); }
    public static Component bookKey() { return BOOK.getTranslatedKeyMessage(); }
    public static Component toggleKey() { return TOGGLE.getTranslatedKeyMessage(); }
    public static Component instantKey() { return INSTANT.getTranslatedKeyMessage(); }
    public static boolean bookMatches(int key, int scan) { return BOOK.matches(key, scan); }
    public static boolean shapeMatches(int key, int scan) { return SHAPE.matches(key, scan); }
    public static Component castHint(String intent) {
        return Component.literal((intent.equals("self") ? "Self: " : "Primary: ")).append(intent.equals("self") ? SELF.getTranslatedKeyMessage() : DIRECTED.getTranslatedKeyMessage());
    }
    private static KeyMapping key(String id, InputConstants.Type type, int code) {
        return new KeyMapping("key.magicka." + id, KeyConflictContext.IN_GAME, type, code, "key.categories.magicka");
    }
    private static boolean playable() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.screen == null && !mc.player.isSpectator();
    }
    private static boolean dispatch(java.util.function.Predicate<KeyMapping> matches) {
        if (!playable()) return false;
        for(int i=0;i<4;i++)if(matches.test(QUICK[i]) && QUICK[i].getKeyModifier().isActive(QUICK[i].getKeyConflictContext())){
            int formula=BookPreferences.get().slot(i);
            if(formula!=0)PacketDistributor.sendToServer(new MagicNetwork.QuickFill(formula));
            else Minecraft.getInstance().player.displayClientMessage(Component.translatable("hud.magicka.quick_empty",i+1),true);
            return true;
        }
        if (matches.test(BOOK)) { Minecraft.getInstance().setScreen(new SpellbookScreen()); return true; }
        if (matches.test(SHAPE)) { Minecraft.getInstance().setScreen(new ShapeEditorScreen()); return true; }
        if (matches.test(SELF) && Minecraft.getInstance().player.getMainHandItem().is(com.restor.magicka.MagicItems.SPELLBOOK.get())) {
            Minecraft.getInstance().setScreen(new SpellbookScreen()); return true;
        }
        var state = MagicNetwork.clientState;
        if (matches.test(TOGGLE)) {
            if (state.remaining() == 0) {
                send(7);
                MagicNetwork.clientState = new MagicNetwork.State(!state.enabled(), state.formula(), 0, 0, 0, state.budget(), state.instant(), false);
            }
            return true;
        }
        if (matches.test(INSTANT)) {
            send(11);
            MagicNetwork.clientState = new MagicNetwork.State(state.enabled(), state.formula(), state.remaining(), state.total(), state.intent(), state.budget(), !state.instant(), state.channeling());
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(state.instant() ? "hud.magicka.mode_normal" : "hud.magicka.mode_instant"), true);
            return true;
        }
        if (!state.enabled()) return false;
        for (int i = 0; i < ELEMENTS.length; i++) if (matches.test(ELEMENTS[i])) { send(elementAction(Element.values()[i])); return true; }
        if (matches.test(CLEAR)) { send(8); return true; }
        if (matches.test(DIRECTED)) { send(9); return true; }
        if (matches.test(SELF)) { send(10); return true; }
        return false;
    }
    public static int elementAction(Element element) { return element == Element.DEATH ? 13 : element.ordinal(); }
    private static void send(int action) { PacketDistributor.sendToServer(new MagicNetwork.Input(action)); }
    @SubscribeEvent public static void movement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        int mask=MagicNetwork.clientStatuses.getOrDefault(event.getEntity().getId(),0);
        int frozen=(1<<com.restor.magicka.MagicStatuses.Status.FROZEN.ordinal())|(1<<com.restor.magicka.MagicStatuses.Status.STUNNED.ordinal())|(1<<com.restor.magicka.MagicStatuses.Status.ROOTED.ordinal());
        if((mask&frozen)!=0){var input=event.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.jumping=false;}
    }
    @SubscribeEvent public static void useBook(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide && event.getItemStack().is(com.restor.magicka.MagicItems.SPELLBOOK.get())) {
            Minecraft.getInstance().setScreen(new SpellbookScreen());
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
    /** Drops the vanilla action bound to the same key, so an element key cannot also open the inventory. */
    private static void suppressVanilla(int keyCode, int scanCode) {
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            if ("key.categories.magicka".equals(key.getCategory()) || !key.matches(keyCode, scanCode)) continue;
            while (key.consumeClick()) { }
            key.setDown(false);
        }
    }
    private static void release(java.util.function.Predicate<KeyMapping> matches) {
        if (MagicNetwork.clientState.channeling() && matches.test(DIRECTED)) send(12);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void keyboard(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) { release(key -> key.matches(event.getKey(), event.getScanCode())); return; }
        if (!playable()) return;
        boolean handled = event.getAction() == GLFW.GLFW_PRESS && dispatch(key -> key.matches(event.getKey(), event.getScanCode()));
        boolean repeatedElement = MagicNetwork.clientState.enabled() && java.util.Arrays.stream(ELEMENTS)
                .anyMatch(key -> key.matches(event.getKey(), event.getScanCode()));
        repeatedElement |= java.util.Arrays.stream(QUICK).anyMatch(key->key.matches(event.getKey(),event.getScanCode()) && key.getKeyModifier().isActive(key.getKeyConflictContext()));
        if (handled || repeatedElement) suppressVanilla(event.getKey(), event.getScanCode());
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) release(key -> key.matchesMouse(event.getButton()));
        if (event.getAction() == GLFW.GLFW_PRESS && dispatch(key -> key.matchesMouse(event.getButton()))) event.setCanceled(true);
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event) {
        if (playable() && MagicNetwork.clientState.enabled() && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        MagicNetwork.clearClient();
        SpellVisuals.clear();
        PolymorphVisuals.clear();
    }
    @SubscribeEvent public static void preview(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !playable()) return;
        var state = MagicNetwork.clientState;
        if (!state.enabled() || state.remaining() > 0 || state.formula()==0 || !com.restor.magicka.RecipeRegistry.clientResolve(Formula.unpack(state.formula()).key()).shape().construction()) return;
        var mc = Minecraft.getInstance();
        if (!com.restor.magicka.MagicConfig.CONSTRUCTION.get()) return;
        Formula formula = Formula.unpack(state.formula());
        var recipe = com.restor.magicka.RecipeRegistry.clientResolve(formula.key());
        var path = new com.restor.magicka.MagicConstructionManager.Plan(mc.player, recipe, false, com.restor.magicka.SpellRuntime.origin(mc.player,recipe.origin()));
        var camera = event.getCamera().getPosition();
        var stack = event.getPoseStack();
        stack.pushPose(); stack.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource();
        boolean stencil = com.restor.magicka.SpellDefinition.string(recipe.construction(),"template","").equals("custom");
        int shown = stencil && path.template != null ? path.template.size() : Math.min(3, state.budget());
        for (int i = 0; i < shown; i++) {
            var pos = path.next(mc.player, i);
            boolean free = mc.level.getBlockState(pos).isAir();
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(stack, buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines()),
                    new net.minecraft.world.phys.AABB(pos).inflate(0.002), free ? 0.5F : 1F, free ? 0.9F : 0.3F, 0.95F, 0.7F);
        }
        stack.popPose();
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }
    @EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            for (KeyMapping key : ELEMENTS) event.register(key);
            for(KeyMapping key:QUICK)event.register(key);
            for (KeyMapping key : new KeyMapping[]{TOGGLE, CLEAR, DIRECTED, SELF, INSTANT, BOOK, SHAPE}) event.register(key);
        }
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Magicka.ID, "casting"), (graphics, timer) -> MagicHud.render(graphics));
        }
    }
    private MagicClient() {}
}
