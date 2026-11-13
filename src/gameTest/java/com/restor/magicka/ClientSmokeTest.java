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
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen && mc.getOverlay() == null) {
            mc.setScreen(new TitleScreen());
        }
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null && ++ticks == 30) {
            long keys = java.util.Arrays.stream(mc.options.keyMappings).filter(k -> k.getName().startsWith("key.magicka.")).count();
            if (keys != 32) throw new IllegalStateException("Expected 32 Magicka key mappings, got " + keys);
            if (RecipeRegistry.clientRecipes().size()!=RecipeRegistry.CATALOG_SIZE)throw new IllegalStateException("Client catalog must contain all "+RecipeRegistry.CATALOG_SIZE+" spells, got "+RecipeRegistry.clientRecipes().size());
            for(String id:com.restor.magicka.client.enemy.EnemyLayers.ALL.keySet()){
                mc.getEntityModels().bakeLayer(com.restor.magicka.client.enemy.EnemyRenderer.layer(id));
                if(mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation(Magicka.ID,"textures/entity/"+id+".png")).isEmpty())throw new IllegalStateException("Missing enemy texture "+id);
            }
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_CLIENT_SMOKE_OK: resources loaded; {} key mappings registered", keys);
            net.minecraftforge.fml.config.ConfigTracker.INSTANCE.loadDefaultServerConfigs();
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.reloadResourcePacks();
            var preferences=com.restor.magicka.client.BookPreferences.get();preferences.query="";preferences.tag="all";preferences.size=0;preferences.elements=0;preferences.selectedId="magicka:stone_stair";
            String[] presets={"Firebomb","Stone Stair","Healing Pool","Arcane Meteor"};
            for(int i=0;i<4;i++){String name=presets[i];preferences.assign(i,RecipeRegistry.clientRecipes().stream().filter(d->d.name().equals(name)).findFirst().orElseThrow().spokenPacked());}
            mc.setScreen(new com.restor.magicka.client.GrimoireScreen());
            ticks = 0; stage = 1;
        } else if (stage == 1 && mc.getOverlay() == null) {
            if(++ticks!=40)return;
            if (net.minecraft.client.resources.language.I18n.get("book.magicka.title").contains("?")) throw new IllegalStateException("Broken Russian text");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-book.png", mc.getMainRenderTarget(), message -> {});
            ticks = 0; stage = 5;
        } else if (stage == 5 && ++ticks == 10) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-stats.png", mc.getMainRenderTarget(), message -> {});
            ticks = 0; stage = 2;
            MagicNetwork.clientBalance=new MagicNetwork.Balance(true,15,43,100,67,100);
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("HUD preview")) {
                @Override public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float partial) {
                    g.fill(0, 0, width, height, 0xFF28333D);
                    int cx = width / 2;
                    g.fill(cx - 91, height - 22, cx + 91, height, 0xFF56555A);
                    for (int i = 0; i < 9; i++) g.renderOutline(cx - 90 + i * 20, height - 21, 20, 20, 0xFFAAAAAA);
                    for (int i = 0; i < 10; i++) g.blit(new net.minecraft.resources.ResourceLocation("textures/gui/icons.png"), cx - 91 + i * 8, height - 39, 52, 0, 9, 9);
                    var f = new Formula(); f.add(Element.WATER); f.add(Element.COLD); f.add(Element.EARTH);
                    com.restor.magicka.client.MagicHud.renderLayoutPreview(g, new MagicNetwork.State(true, f.packed(), 20, 60, 0, 8, true, false), 49);
                }
            });
        } else if (stage == 2 && ++ticks == 30) {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "magicka-hud.png", mc.getMainRenderTarget(), message -> {});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_UI_SMOKE_OK: grimoire and HUD rendered in Russian");
            ticks=0;stage=12;mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();
            mc.setScreen(new com.restor.magicka.client.GrimoireScreen());
        } else if(stage==12 && mc.getOverlay()==null && ++ticks==30){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-book-en.png",mc.getMainRenderTarget(),message->{});
            ticks=0;stage=13;com.restor.magicka.client.MagicClient.openSettings(new TitleScreen());
        } else if((stage==13||stage==15) && mc.getOverlay()==null && ++ticks==15){
            String locale=stage==13?"en":"ru";
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-"+locale+".png",mc.getMainRenderTarget(),message->{});
            String key="settings.magicka.category.balance";
            var button=findButton(mc.screen,net.minecraft.client.resources.language.I18n.get(key));
            if(button==null||!button.active)throw new IllegalStateException("Balance settings section unavailable");
            if(BalanceConfig.SPEC.getValues().valueMap().size()!=37)throw new IllegalStateException("Missing balance settings");
            button.onPress();ticks=0;stage++;
        } else if(stage==14 && ++ticks==15){
            if(!(mc.screen instanceof com.restor.magicka.client.SpellSettingsScreen settings)||settings.optionCount()!=ConfigCommand.values().size())throw new IllegalStateException("Modern settings did not open");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-balance-en.png",mc.getMainRenderTarget(),message->{});
            ticks=0;stage=15;mc.options.languageCode="ru_ru";mc.getLanguageManager().setSelected("ru_ru");mc.reloadResourcePacks();
            com.restor.magicka.client.MagicClient.openSettings(new TitleScreen());
        } else if(stage==16 && ++ticks==15){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-balance-ru.png",mc.getMainRenderTarget(),message->{});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_SETTINGS_SMOKE_OK: modern settings and categories opened in English and Russian");
            var button=findButton(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.category.cooldowns"));if(button==null)throw new IllegalStateException("Cooldown category missing");button.onPress();ticks=0;stage=17;
        } else if(stage==17 && ++ticks==15){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-cooldowns.png",mc.getMainRenderTarget(),message->{});
            var button=findButton(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.entries",BalanceConfig.RECOVERY_TICKS.get().size()));if(button==null)throw new IllegalStateException("Cooldown list editor missing");button.onPress();ticks=0;stage=18;
        } else if(stage==18 && ++ticks==15){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-list.png",mc.getMainRenderTarget(),message->{});mc.screen.onClose();
            var search=findInput(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.search"));if(search==null)throw new IllegalStateException("Settings search missing");search.setValue("manaCapacity");ticks=0;stage=19;
        } else if(stage==19 && ++ticks==20){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-search.png",mc.getMainRenderTarget(),message->{});
            org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_SETTINGS_EDITORS_OK: cooldown list editor and settings search rendered");
            var button=findExactButton(mc.screen,"/");if(button==null)throw new IllegalStateException("Per-option command help missing");button.onPress();ticks=0;stage=20;
        } else if(stage==20 && ++ticks==15){
            var field=findInput(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.command_set"));if(field==null||!field.getValue().startsWith("/spellweaver config balance.manaCapacity "))throw new IllegalStateException("Command targets the wrong option");
            String clipboard=mc.keyboardHandler.getClipboard();try{var copy=findExactButton(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.copy"));if(copy==null)throw new IllegalStateException("Command copy button missing");copy.onPress();if(!mc.keyboardHandler.getClipboard().equals("/spellweaver config balance.manaCapacity"))throw new IllegalStateException("Wrong command copied");}finally{mc.keyboardHandler.setClipboard(clipboard);}
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-commands.png",mc.getMainRenderTarget(),message->{});mc.screen.onClose();var button=findExactButton(mc.screen,"/>");if(button==null)throw new IllegalStateException("Server command overview missing");button.onPress();ticks=0;stage=21;
        } else if(stage==21 && ++ticks==15){
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-command-overview.png",mc.getMainRenderTarget(),message->{});mc.screen.onClose();var search=findInput(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.search"));search.setValue("elementCooldownTicks");ticks=0;stage=22;
        } else if(stage==22 && ++ticks==15){
            var button=findExactButton(mc.screen,"/");if(button==null)throw new IllegalStateException("List command help missing");button.onPress();ticks=0;stage=23;
        } else if(stage==23 && ++ticks==15){
            var field=findInput(mc.screen,net.minecraft.client.resources.language.I18n.get("settings.magicka.command_set"));if(field==null||!field.getValue().contains("[\"fire=12\"")||field.getValue().contains("u003d"))throw new IllegalStateException("List command was truncated or escaped incorrectly");
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"magicka-settings-list-commands.png",mc.getMainRenderTarget(),message->{});org.slf4j.LoggerFactory.getLogger("MagickaSmokeTest").info("MAGICKA_SETTINGS_COMMANDS_OK: option commands, clipboard copy, overview and JSON-list commands verified");
            ticks=0;stage=3;MagicNetwork.clientBalance=MagicNetwork.Balance.EMPTY;mc.setScreen(new VfxPreviewScreen());
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
