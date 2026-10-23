package com.restor.magicka.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.restor.magicka.CharmItem;
import com.restor.magicka.Element;
import com.restor.magicka.Formula;
import com.restor.magicka.Magicka;
import com.restor.magicka.MagicNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

@EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class MagicClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Magicka.ID, "main"));
    static {MagicNetwork.visualReceiver=SpellVisuals::receive;}
    @SubscribeEvent public static void charms(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        CharmItem.describe(event.getItemStack(), event.getToolTip()::add);
    }
    @SubscribeEvent public static void visualsTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        SpellVisuals.tick();
        var player=Minecraft.getInstance().player;
        if(player!=null && MagicNetwork.clientState.channeling() && !directedHeld && player.tickCount%5==0)send(12);
    }
    @SubscribeEvent public static void visuals(net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent event){
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getLevelRenderState().cameraRenderState.pos;pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        SpellVisuals.render(pose,event.getSubmitNodeCollector(),camera,mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        preview(pose,event.getSubmitNodeCollector());
        pose.popPose();
    }
    // Elements stay under the resting left hand: 1-4 above WASD, Q E R F around it.
    private static final int[] ELEMENT_KEYS = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
            InputConstants.KEY_Q, InputConstants.KEY_E, InputConstants.KEY_R, InputConstants.KEY_F};
    private static final KeyMapping[] ELEMENTS = java.util.Arrays.stream(Element.values()).map(e ->
            key(e.name().toLowerCase(java.util.Locale.ROOT), InputConstants.Type.KEYSYM, ELEMENT_KEYS[e.ordinal()])).toArray(KeyMapping[]::new);
    private static final KeyMapping TOGGLE = key("toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_G);
    private static final KeyMapping CLEAR = key("clear", InputConstants.Type.KEYSYM, InputConstants.KEY_V);
    private static final KeyMapping DIRECTED = key("directed", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_LEFT);
    private static final KeyMapping SELF = key("self", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT);
    private static final KeyMapping BOOK = key("spellbook", InputConstants.Type.KEYSYM, InputConstants.KEY_B);
    private static final KeyMapping INSTANT = key("instant", InputConstants.Type.KEYSYM, InputConstants.KEY_H);
    private static final KeyMapping SHAPE = key("shape", InputConstants.Type.KEYSYM, InputConstants.KEY_K);
    private static final KeyMapping LOADOUT = key("loadout", InputConstants.Type.KEYSYM, InputConstants.KEY_J);
    private static final KeyMapping[] QUICK=java.util.stream.IntStream.range(0,4).mapToObj(i->new KeyMapping("key.magicka.quick_"+(i+1),KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.ALT,InputConstants.Type.KEYSYM,InputConstants.KEY_1+i,CATEGORY)).toArray(KeyMapping[]::new);
    public static Component quickKey(int index){return QUICK[index].getTranslatedKeyMessage();}
    public static Component elementKey(Element element) { return ELEMENTS[element.ordinal()].getTranslatedKeyMessage(); }
    public static Component bookKey() { return BOOK.getTranslatedKeyMessage(); }
    public static Component toggleKey() { return TOGGLE.getTranslatedKeyMessage(); }
    public static Component instantKey() { return INSTANT.getTranslatedKeyMessage(); }
    public static boolean bookMatches(KeyEvent event) { return BOOK.matches(event); }
    public static boolean shapeMatches(KeyEvent event) { return SHAPE.matches(event); }
    public static Component loadoutKey() { return LOADOUT.getTranslatedKeyMessage(); }
    public static void openLoadout() { ClientPacketDistributor.sendToServer(new MagicNetwork.Input(14)); }
    public static Component castHint(String intent) {
        return Component.literal((intent.equals("self") ? "Self: " : "Primary: ")).append(intent.equals("self") ? SELF.getTranslatedKeyMessage() : DIRECTED.getTranslatedKeyMessage());
    }
    private static KeyMapping key(String id, InputConstants.Type type, int code) {
        return new KeyMapping("key.magicka." + id, KeyConflictContext.IN_GAME, type, code, CATEGORY);
    }
    private static boolean playable() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null && mc.gui.screen() == null && !mc.player.isSpectator();
    }
    private static boolean dispatch(java.util.function.Predicate<KeyMapping> matches) {
        if (!playable()) return false;
        for(int i=0;i<4;i++)if(matches.test(QUICK[i]) && QUICK[i].getKeyModifier().isActive(QUICK[i].getKeyConflictContext())){
            int formula=BookPreferences.get().slot(i);
            if(formula!=0)ClientPacketDistributor.sendToServer(new MagicNetwork.QuickFill(formula));
            else Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("hud.magicka.quick_empty",i+1));
            return true;
        }
        if (matches.test(BOOK)) { Minecraft.getInstance().gui.setScreen(new SpellbookScreen()); return true; }
        if (matches.test(SHAPE)) { Minecraft.getInstance().gui.setScreen(new ShapeEditorScreen()); return true; }
        if (matches.test(LOADOUT)) { send(14); return true; }
        if (matches.test(SELF) && Minecraft.getInstance().player.getMainHandItem().is(com.restor.magicka.MagicItems.SPELLBOOK.get())) {
            Minecraft.getInstance().gui.setScreen(new SpellbookScreen()); return true;
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
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable(state.instant() ? "hud.magicka.mode_normal" : "hud.magicka.mode_instant"));
            return true;
        }
        if (!state.enabled()) return false;
        for (int i = 0; i < ELEMENTS.length; i++) if (matches.test(ELEMENTS[i])) { send(elementAction(Element.values()[i])); return true; }
        if (matches.test(CLEAR)) { send(8); return true; }
        if (matches.test(DIRECTED)) { directedHeld = true; send(9); return true; }
        if (matches.test(SELF)) { send(10); return true; }
        return false;
    }
    public static int elementAction(Element element) { return element == Element.DEATH ? 13 : element.ordinal(); }
    private static void send(int action) { ClientPacketDistributor.sendToServer(new MagicNetwork.Input(action)); }
    @SubscribeEvent public static void movement(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event) {
        int mask=MagicNetwork.clientStatuses.getOrDefault(event.getEntity().getId(),0);
        int frozen=(1<<com.restor.magicka.MagicStatuses.Status.FROZEN.ordinal())|(1<<com.restor.magicka.MagicStatuses.Status.STUNNED.ordinal())|(1<<com.restor.magicka.MagicStatuses.Status.ROOTED.ordinal());
        if((mask&frozen)!=0){var input=event.getInput();input.moveVector=net.minecraft.world.phys.Vec2.ZERO;input.keyPresses=new net.minecraft.world.entity.player.Input(false,false,false,false,false,input.keyPresses.shift(),false);}
    }
    @SubscribeEvent public static void useBook(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() && event.getItemStack().is(com.restor.magicka.MagicItems.SPELLBOOK.get())) {
            Minecraft.getInstance().gui.setScreen(new SpellbookScreen());
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
    /** Drops the vanilla action bound to the same key, so an element key cannot also open the inventory. */
    private static void suppressVanilla(KeyEvent event) {
        for (KeyMapping key : Minecraft.getInstance().options.keyMappings) {
            if (key.getCategory() == CATEGORY || !key.matches(event)) continue;
            while (key.consumeClick()) { }
            key.setDown(false);
        }
    }
    private static boolean directedHeld;
    private static void release(java.util.function.Predicate<KeyMapping> matches) {
        if (!matches.test(DIRECTED)) return;
        directedHeld = false;
        if (MagicNetwork.clientState.channeling()) send(12);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void keyboard(InputEvent.Key event) {
        KeyEvent input = event.getKeyEvent();
        if (event.getAction() == InputConstants.RELEASE) { release(key -> key.matches(input)); return; }
        if (!playable()) return;
        boolean handled = event.getAction() == InputConstants.PRESS && dispatch(key -> key.matches(input));
        boolean repeatedElement = MagicNetwork.clientState.enabled() && java.util.Arrays.stream(ELEMENTS).anyMatch(key -> key.matches(input));
        repeatedElement |= java.util.Arrays.stream(QUICK).anyMatch(key->key.matches(input) && key.getKeyModifier().isActive(key.getKeyConflictContext()));
        if (handled || repeatedElement) suppressVanilla(input);
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        MouseButtonEvent input = new MouseButtonEvent(0, 0, event.getMouseButtonInfo());
        if (event.getAction() == InputConstants.RELEASE) release(key -> key.matchesMouse(input));
        if (event.getAction() == InputConstants.PRESS && dispatch(key -> key.matchesMouse(input))) event.setCanceled(true);
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
    private static void preview(com.mojang.blaze3d.vertex.PoseStack stack, net.minecraft.client.renderer.SubmitNodeCollector collector) {
        if (!playable()) return;
        var state = MagicNetwork.clientState;
        if (!state.enabled() || state.remaining() > 0 || state.formula()==0 || !com.restor.magicka.RecipeRegistry.clientResolve(Formula.unpack(state.formula()).key()).shape().construction()) return;
        var mc = Minecraft.getInstance();
        if (!com.restor.magicka.MagicConfig.CONSTRUCTION.get()) return;
        Formula formula = Formula.unpack(state.formula());
        var recipe = com.restor.magicka.RecipeRegistry.clientResolve(formula.key());
        var path = new com.restor.magicka.MagicConstructionManager.Plan(mc.player, recipe, false, com.restor.magicka.SpellRuntime.origin(mc.player,recipe.origin()));
        boolean stencil = com.restor.magicka.SpellDefinition.string(recipe.construction(),"template","").equals("custom");
        int shown = stencil && path.template != null ? path.template.size() : Math.min(3, state.budget());
        var boxes = new java.util.ArrayList<net.minecraft.world.phys.AABB>();
        var colors = new java.util.ArrayList<Integer>();
        for (int i = 0; i < shown; i++) {
            var pos = path.next(mc.player, i);
            colors.add(mc.level.getBlockState(pos).isAir() ? 0xB380E6F2 : 0xB3FF4CF2);
            boxes.add(new net.minecraft.world.phys.AABB(pos).inflate(0.002));
        }
        collector.submitCustomGeometry(stack, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (pose, buffer) -> {
            for (int i = 0; i < boxes.size(); i++) box(pose, buffer, boxes.get(i), colors.get(i));
        });
    }
    private static void box(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.world.phys.AABB box, int color) {
        float[] x = {(float) box.minX, (float) box.maxX}, y = {(float) box.minY, (float) box.maxY}, z = {(float) box.minZ, (float) box.maxZ};
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            line(pose, buffer, x[0], y[a], z[b], x[1], y[a], z[b], color);
            line(pose, buffer, x[a], y[0], z[b], x[a], y[1], z[b], color);
            line(pose, buffer, x[a], y[b], z[0], x[a], y[b], z[1], color);
        }
    }
    private static void line(com.mojang.blaze3d.vertex.PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0, length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        buffer.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx / length, ny / length, nz / length).setLineWidth(2F);
        buffer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx / length, ny / length, nz / length).setLineWidth(2F);
    }
    @EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
            for (KeyMapping key : ELEMENTS) event.register(key);
            for(KeyMapping key:QUICK)event.register(key);
            for (KeyMapping key : new KeyMapping[]{TOGGLE, CLEAR, DIRECTED, SELF, INSTANT, BOOK, SHAPE, LOADOUT}) event.register(key);
        }
        @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
            event.register(com.restor.magicka.GearMenu.TYPE.get(), GearScreen::new);
        }
        @SubscribeEvent public static void layers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(Identifier.fromNamespaceAndPath(Magicka.ID, "casting"), (graphics, timer) -> MagicHud.render(graphics));
        }
    }
    private MagicClient() {}
}
