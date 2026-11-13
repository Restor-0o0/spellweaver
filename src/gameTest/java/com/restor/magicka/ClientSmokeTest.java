package com.restor.magicka;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only check: finish resource loading, verify bindings, then close the client. */
@EventBusSubscriber(modid = Magicka.ID, value = Dist.CLIENT)
public final class ClientSmokeTest {
    private static int ticks;
    private static String layout;
    private static int stage;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magicka.clientSmokeTest")) return;
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen && mc.gui.overlay() == null) {
            mc.gui.setScreen(new TitleScreen());
        }
        if (stage == 0 && mc.gui.screen() instanceof TitleScreen && mc.gui.overlay() == null && ++ticks == 30) {
            long keys = java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().startsWith("key.magicka.")).count();
            if (keys != 32) throw new IllegalStateException("Expected 32 Magicka key mappings, got " + keys);
            if (RecipeRegistry.clientRecipes().size()!=RecipeRegistry.CATALOG_SIZE)throw new IllegalStateException("Client catalog must contain all "+RecipeRegistry.CATALOG_SIZE+" spells, got "+RecipeRegistry.clientRecipes().size());
            for(String id:com.restor.magicka.client.enemy.EnemyLayers.ALL.keySet()){
                mc.getEntityModels().bakeLayer(com.restor.magicka.client.enemy.EnemyRenderer.layer(id));
                if(mc.getResourceManager().getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath(Magicka.ID,"textures/entity/"+id+".png")).isEmpty())throw new IllegalStateException("Missing enemy texture "+id);
            }
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_CLIENT_SMOKE_OK: resources loaded; {} key mappings registered", keys);
            net.neoforged.fml.config.ConfigTracker.INSTANCE.loadDefaultSyncedConfigs();
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.reloadResourcePacks();
            layout=com.restor.magicka.client.MagicHudLayout.exportCode();if(com.restor.magicka.client.MagicHudLayout.visible(com.restor.magicka.client.MagicHudLayout.Widget.BOOK_HINT))com.restor.magicka.client.MagicHudLayout.toggleVisible(com.restor.magicka.client.MagicHudLayout.Widget.BOOK_HINT);
            var preferences=com.restor.magicka.client.BookPreferences.get();preferences.query="";preferences.tag="all";preferences.size=0;preferences.elements=0;preferences.selectedId="magicka:stone_stair";
            String[] presets={"Firebomb","Stone Stair","Healing Pool","Arcane Meteor"};
            for(int i=0;i<4;i++){String name=presets[i];preferences.assign(i,RecipeRegistry.clientRecipes().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow().spokenPacked());}
            mc.gui.setScreen(new com.restor.magicka.client.GrimoireScreen());
            ticks = 0; stage = 1;
        } else if (stage == 1 && mc.gui.overlay() == null) {
            if(++ticks!=40)return;
            if (net.minecraft.client.resources.language.I18n.get("book.magicka.title").contains("?")) throw new IllegalStateException("Broken Russian text");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-book.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            ticks = 0; stage = 5;
        } else if (stage == 5 && ++ticks == 10) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-stats.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            ticks = 0; stage = 2;
            mc.gui.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("HUD preview")) {
                @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, float partial) {
                    g.fill(0, 0, width, height, 0xFF28333D);
                    int cx = width / 2;
                    g.fill(cx - 91, height - 22, cx + 91, height, 0xFF56555A);
                    for (int i = 0; i < 9; i++) g.outline(cx - 90 + i * 20, height - 21, 20, 20, 0xFFAAAAAA);
                    for (int i = 0; i < 10; i++) g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.resources.Identifier.withDefaultNamespace("hud/heart/full"), cx - 91 + i * 8, height - 39, 9, 9);
                    var f = new Formula(); f.add(Element.WATER); f.add(Element.COLD); f.add(Element.EARTH);
                    com.restor.magicka.client.MagicHud.renderLayoutPreview(g, new MagicNetwork.State(true, f.packed(), 20, 60, 0, 8, true, false), 49);
                }
            });
        } else if (stage == 2 && ++ticks == 30) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-hud.png", mc.gameRenderer.mainRenderTarget(), 1, message -> {});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_UI_SMOKE_OK: grimoire and HUD rendered in Russian");
            com.restor.magicka.client.MagicHudLayout.importCode(layout);MagicNetwork.clientBalance=MagicNetwork.Balance.EMPTY;
            ticks=0;stage=100;com.restor.magicka.client.MagicClient.openSettings(new TitleScreen());
        } else if(stage==100 && mc.gui.overlay()==null && ++ticks==20){
            if(!(mc.gui.screen() instanceof com.restor.magicka.client.SpellSettingsScreen settings)||settings.optionCount()!=82)throw new IllegalStateException("Missing settings screen");shot(mc,"magicka-settings-ru.png");
            mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();ticks=0;stage=101;com.restor.magicka.client.MagicClient.openSettings(new TitleScreen());
        } else if(stage==101 && mc.gui.overlay()==null && ++ticks==20){
            shot(mc,"magicka-settings-en.png");var category=findButton(mc.gui.screen(),net.minecraft.client.resources.language.I18n.get("settings.magicka.category.cooldowns"));if(category==null)throw new IllegalStateException("Cooldown category missing");category.onPress(null);ticks=0;stage=102;
        } else if(stage==102 && ++ticks==10){
            var list=findButton(mc.gui.screen(),net.minecraft.client.resources.language.I18n.get("settings.magicka.entries",BalanceConfig.RECOVERY_TICKS.getDefault().size()));if(list==null)throw new IllegalStateException("List editor missing");list.onPress(null);ticks=0;stage=103;
        } else if(stage==103 && ++ticks==10){
            shot(mc,"magicka-settings-list.png");mc.gui.screen().onClose();var help=findExactButton(mc.gui.screen(),"/>");if(help==null)throw new IllegalStateException("Command help missing");help.onPress(null);ticks=0;stage=104;
        } else if(stage==104 && ++ticks==10){
            shot(mc,"magicka-settings-commands.png");org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_SETTINGS_SMOKE_OK: 82 settings, Russian/English, list editor and command help rendered");mc.stop();
        }
    }
    private static void shot(Minecraft mc,String name){net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, message -> {});}
    private static net.minecraft.client.gui.components.Button findButton(net.minecraft.client.gui.components.events.GuiEventListener listener,String text){
        if(listener instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().contains(text))return button;
        if(listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)for(var child:container.children()){var found=findButton(child,text);if(found!=null)return found;}
        return null;
    }
    private static net.minecraft.client.gui.components.EditBox findInput(net.minecraft.client.gui.components.events.GuiEventListener listener,String text){
        if(listener instanceof net.minecraft.client.gui.components.EditBox field && field.getMessage().getString().equals(text))return field;
        if(listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)for(var child:container.children()){var found=findInput(child,text);if(found!=null)return found;}
        return null;
    }
    private static net.minecraft.client.gui.components.Button findExactButton(net.minecraft.client.gui.components.events.GuiEventListener listener,String text){
        if(listener instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(text))return button;
        if(listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)for(var child:container.children()){var found=findExactButton(child,text);if(found!=null)return found;}
        return null;
    }
}
