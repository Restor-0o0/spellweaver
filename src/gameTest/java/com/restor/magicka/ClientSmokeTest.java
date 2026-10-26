package com.restor.magicka;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Development-only check: finish resource loading, verify bindings, then close the client. */
@Mod.EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static int ticks;
    private static int stage;
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!Boolean.getBoolean("magicka.clientSmokeTest")) return;
        var mc = Minecraft.getInstance();
        if (false) {
            mc.setScreen(new TitleScreen());
        }
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++ticks == 30) {
            long keys = java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().startsWith("key.magicka.")).count();
            if (keys != 22) throw new IllegalStateException("Expected 22 Magicka key mappings, got " + keys);
            if (RecipeRegistry.clientRecipes().size()!=RecipeRegistry.CATALOG_SIZE)throw new IllegalStateException("Client catalog must contain all "+RecipeRegistry.CATALOG_SIZE+" spells, got "+RecipeRegistry.clientRecipes().size());
            for(String id:com.restor.magicka.client.enemy.EnemyLayers.ALL.keySet()){
                mc.getEntityModels().bakeLayer(com.restor.magicka.client.enemy.EnemyRenderer.layer(id));
                if(mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation(Magicka.ID,"textures/entity/"+id+".png")).isEmpty())throw new IllegalStateException("Missing enemy texture "+id);
            }
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_CLIENT_SMOKE_OK: resources loaded; {} key mappings registered", keys);
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected(mc.getLanguageManager().getLanguage("ru_ru"));
            mc.reloadResourcePacks();
            var preferences=com.restor.magicka.client.BookPreferences.get();preferences.query="";preferences.tag="all";preferences.size=0;preferences.elements=0;preferences.selectedId="magicka:stone_stair";
            String[] presets={"Firebomb","Stone Stair","Healing Pool","Arcane Meteor"};
            for(int i=0;i<4;i++){String name=presets[i];preferences.assign(i,RecipeRegistry.clientRecipes().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow().spokenPacked());}
            mc.setScreen(new com.restor.magicka.client.GrimoireScreen());
            ticks = 0; stage = 1;
        } else if (stage == 1 && mc.getOverlay() == null) {
            if(ticks!=40)return;
            if (net.minecraft.client.resources.language.I18n.get("book.magicka.title").contains("?")) throw new IllegalStateException("Broken Russian text");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-book.png", mc.getMainRenderTarget(), message -> {});
            ticks = 0; stage = 5;
        } else if (stage == 5 && ++ticks == 10) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-stats.png", mc.getMainRenderTarget(), message -> {});
            ticks = 0; stage = 2;
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("HUD preview")) {
                @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mx, int my, float partial) {var g=new com.restor.magicka.client.GuiGraphics(pose);
                    g.fill(0, 0, width, height, 0xFF28333D);
                    int cx = width / 2;
                    g.fill(cx - 91, height - 22, cx + 91, height, 0xFF56555A);
                    for (int i = 0; i < 9; i++) g.renderOutline(cx - 90 + i * 20, height - 21, 20, 20, 0xFFAAAAAA);
                    for (int i = 0; i < 10; i++) g.blit(new net.minecraft.resources.ResourceLocation("textures/gui/icons.png"), cx - 91 + i * 8, height - 39, 52, 0, 9, 9);
                    var f = new Formula(); f.add(Element.WATER); f.add(Element.COLD); f.add(Element.EARTH);
                    com.restor.magicka.client.MagicHud.render(g, new MagicNetwork.State(true, f.packed(), 20, 60, 0, 8, true, false), 49);
                }
            });
        } else if (stage == 2 && ++ticks == 30) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-hud.png", mc.getMainRenderTarget(), message -> {});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_UI_SMOKE_OK: grimoire and HUD rendered in Russian");
            ticks=0;stage=3;mc.setScreen(new VfxPreviewScreen());
        } else if(stage==3 && ++ticks==30){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-vfx.png",mc.getMainRenderTarget(),message->{});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_VFX_SMOKE_OK: world ribbons, rings and vanilla block cores rendered");
            ticks=0;stage=6;mc.setScreen(new EnemyPreviewScreen(35,EnemyPreviewScreen.Kind.ENEMIES));
        } else if(stage==6 && ++ticks==20){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-enemies.png",mc.getMainRenderTarget(),message->{});
            ticks=0;stage=7;mc.setScreen(new EnemyPreviewScreen(215,EnemyPreviewScreen.Kind.ENEMIES));
        } else if(stage==7 && ++ticks==20){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-enemies-back.png",mc.getMainRenderTarget(),message->{});
            ticks=0;stage=8;mc.setScreen(new EnemyPreviewScreen(35,EnemyPreviewScreen.Kind.ELEMENTALS));
        } else if(stage>=8 && stage<=10 && ++ticks==20){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,new String[]{"magicka-elementals.png","magicka-elementals-back.png","magicka-titans.png"}[stage-8],mc.getMainRenderTarget(),message->{});
            ticks=0;stage++;mc.setScreen(new EnemyPreviewScreen(stage==10?35:215,stage==9?EnemyPreviewScreen.Kind.ELEMENTALS:EnemyPreviewScreen.Kind.TITANS));
        } else if(stage==11 && ++ticks==20){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-titans-back.png",mc.getMainRenderTarget(),message->{});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_ENEMY_SMOKE_OK: {} enemy and elemental models rendered",com.restor.magicka.client.enemy.EnemyLayers.ALL.size());
            mc.stop();
        }
    }
}
