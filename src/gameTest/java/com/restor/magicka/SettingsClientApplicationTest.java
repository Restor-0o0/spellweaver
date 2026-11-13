package com.restor.magicka;

import com.restor.magicka.client.MagicClient;
import com.restor.magicka.client.SpellSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@EventBusSubscriber(modid=Magicka.ID,value=Dist.CLIENT)
public final class SettingsClientApplicationTest {
    private static final String WORLD="settings-ui-audit";
    private static int stage,ticks,watchdog;
    private static final Map<String,Object> initial=new LinkedHashMap<>();
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("SettingsClientAudit");
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> work){
        var mc=Minecraft.getInstance();var integrated=mc.getSingleplayerServer();check(integrated!=null,"Integrated server missing");var id=mc.player.getUUID();
        try{CompletableFuture.runAsync(()->{var player=integrated.getPlayerList().getPlayer(id);check(player!=null,"Server player missing");work.accept(player);},integrated).get(5,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private static <T> T widget(GuiEventListener listener,Class<T> type,java.util.function.Predicate<T> test){if(type.isInstance(listener)&&test.test(type.cast(listener)))return type.cast(listener);if(listener instanceof ContainerEventHandler container)for(var child:container.children()){T found=widget(child,type,test);if(found!=null)return found;}return null;}
    private static Button button(Component label){var result=widget(Minecraft.getInstance().screen,Button.class,b->b.getMessage().getString().equals(label.getString()));check(result!=null,"Missing button "+label.getString());return result;}
    private static EditBox field(Component label){var result=widget(Minecraft.getInstance().screen,EditBox.class,b->b.getMessage().getString().equals(label.getString()));check(result!=null,"Missing field "+label.getString());return result;}
    private static Component text(String key,Object...args){return Component.translatable("settings.magicka."+key,args);}
    private static void search(String key){field(text("search")).setValue(key);}
    private static void apply(){Button button=button(text("apply"));check(button.active,"Apply must be enabled for valid changes");button.onPress();}
    private static void next(int next){stage=next;ticks=0;watchdog=0;}
    private static void open(){MagicClient.openSettings(null);}
    private static void disconnect(int next){var mc=Minecraft.getInstance();next(next);if(mc.level!=null)mc.level.disconnect();mc.disconnect(new TitleScreen());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("magicka.settingsApplicationTest"))return;var mc=Minecraft.getInstance();if(++watchdog>3600)throw new IllegalStateException("Settings audit timed out at stage "+stage);
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen && mc.getOverlay()==null)mc.setScreen(new TitleScreen());
        if(stage==0 && mc.screen instanceof TitleScreen && mc.getOverlay()==null && ++ticks==20){
            Path world=mc.gameDirectory.toPath().resolve("saves").resolve(WORLD).resolve("level.dat");
            if(Files.exists(world))mc.createWorldOpenFlows().openWorld(WORLD,()->{throw new IllegalStateException("Could not reopen audit world");});
            else{
                var settings=new net.minecraft.world.level.LevelSettings(WORLD,net.minecraft.world.level.GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,false,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,settings,new net.minecraft.world.level.levelgen.WorldOptions(34567L,false,false),registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
            }next(1);
        }else if(stage==1 && mc.player!=null && mc.level!=null && mc.getOverlay()==null && ++ticks>40){
            ConfigCommand.values().forEach((key,value)->initial.put(key,value.get()));server(p->{check(!p.hasPermissions(2),"Audit host should not need cheats to edit own world");SettingsData.apply(p,new MagicNetwork.SettingsUpdate(100,"{\"constructiveMagic\":true,\"balance.mode\":\"DEFAULT\",\"balance.manaCapacity\":100,\"balance.manaBaseCost\":2}"));});next(2);
        }else if(stage==2 && ++ticks==20){open();search("constructiveMagic");next(3);
        }else if(stage==3 && ++ticks==5){
            Button toggle=button(text("on"));check(toggle.active,"Host setting must be editable without cheats");toggle.onPress();server(p->check(MagicConfig.CONSTRUCTION.get(),"Draft must not change server settings"));button(Component.translatable("gui.cancel")).onPress();server(p->check(MagicConfig.CONSTRUCTION.get(),"Cancel must discard draft"));LOG.info("SETTINGS_UI_CANCEL_OK");open();search("constructiveMagic");next(4);
        }else if(stage==4 && ++ticks==5){button(text("on")).onPress();apply();next(5);
        }else if(stage==5 && ++ticks==25){
            server(p->{check(!MagicConfig.CONSTRUCTION.get(),"UI boolean did not reach server");check(!WorldPlacement.placeBlock(p,p.blockPosition().above(3),MagicBlocks.EARTH.get().defaultBlockState(),UUID.randomUUID(),p.blockPosition().above(3),true,false,null),"Disabled building still places blocks");});check(!button(text("apply")).active,"Server acknowledgement must clear dirty state");LOG.info("SETTINGS_UI_BOOLEAN_APPLY_OK");search("balance.manaCapacity");next(6);
        }else if(stage==6 && ++ticks==5){
            var field=field(Component.translatable("magicka.configuration.balance.manaCapacity"));field.setValue("-1");check(!button(text("apply")).active,"Invalid input must disable Apply");field.setValue("175");server(p->check(BalanceConfig.MANA_MAX.get()==100,"Numeric draft must not mutate server"));apply();next(7);
        }else if(stage==7 && ++ticks==25){server(p->check(BalanceConfig.MANA_MAX.get()==175,"Numeric UI change not applied"));search("balance.manaBaseCost");next(8);
        }else if(stage==8 && ++ticks==5){field(Component.translatable("magicka.configuration.balance.manaBaseCost")).setValue("9");apply();next(9);
        }else if(stage==9 && ++ticks==25){search("balance.mode");next(10);
        }else if(stage==10 && ++ticks==5){
            Button mode=button(BalanceConfig.Mode.DEFAULT.getTranslatedName());for(int i=0;i<6&&!mode.getMessage().getString().equals(BalanceConfig.Mode.MANA.getTranslatedName().getString());i++)mode.onPress();check(mode.getMessage().getString().equals(BalanceConfig.Mode.MANA.getTranslatedName().getString()),"Cannot select mana mode");apply();next(11);
        }else if(stage==11 && ++ticks==30){
            check(MagicNetwork.clientBalance.manaMax()==175&&MagicNetwork.clientBalance.mana()>=0,"HUD did not receive new mana configuration");
            server(p->{check(BalanceConfig.MODE.get()==BalanceConfig.Mode.MANA&&BalanceConfig.MANA_BASE.get()==9,"Mode/cost did not reach server");SpellBalance.settingsChanged(p);double before=p.getPersistentData().getCompound("magicka_balance").getDouble("mana");CastingServer.input(p,7);CastingServer.input(p,5);CastingServer.input(p,10);double after=p.getPersistentData().getCompound("magicka_balance").getDouble("mana");check(Math.abs(before-after-12)<1e-5,"A real cast did not use the configured mana cost");});LOG.info("SETTINGS_UI_ENUM_RESOURCE_AND_CAST_OK");disconnect(12);
        }else if(stage==12 && mc.player==null && mc.getSingleplayerServer()==null && mc.getOverlay()==null && ++ticks>20){mc.createWorldOpenFlows().openWorld(WORLD,()->{throw new IllegalStateException("Cannot reload audit world");});next(13);
        }else if(stage==13 && mc.player!=null && mc.level!=null && mc.getOverlay()==null && ++ticks>40){
            server(p->check(BalanceConfig.MODE.get()==BalanceConfig.Mode.MANA&&BalanceConfig.MANA_MAX.get()==175&&BalanceConfig.MANA_BASE.get()==9&&!MagicConfig.CONSTRUCTION.get(),"Settings did not persist across integrated server restart"));LOG.info("SETTINGS_UI_RESTART_PERSISTENCE_OK");
            open();search("balance.recoveryTicks");next(20);
        }else if(stage==20 && ++ticks==5){
            button(text("entries",BalanceConfig.RECOVERY_TICKS.get().size())).onPress();field(Component.translatable("magicka.configuration.balance.recoveryTicks")).setValue("basic=37");button(Component.translatable("gui.cancel")).onPress();check(!button(text("apply")).active,"List cancel must discard edited rows");
            button(text("entries",BalanceConfig.RECOVERY_TICKS.get().size())).onPress();var entry=field(Component.translatable("magicka.configuration.balance.recoveryTicks"));entry.setValue("basic=-1");check(!button(text("keep")).active,"Invalid list entry must disable Keep");entry.setValue("basic=37");button(text("keep")).onPress();server(p->check(!BalanceConfig.RECOVERY_TICKS.get().contains("basic=37"),"Keep must only update the draft"));apply();next(21);
        }else if(stage==21 && ++ticks==25){
            server(p->check(BalanceConfig.RECOVERY_TICKS.get().contains("basic=37"),"List edit did not reach server"));button(Component.literal("↺")).onPress();apply();next(22);
        }else if(stage==22 && ++ticks==25){
            server(p->check(BalanceConfig.RECOVERY_TICKS.get().equals(BalanceConfig.RECOVERY_TICKS.getDefault()),"Row reset did not restore list default"));button(Component.translatable(SettingsData.Category.MANA.key())).onPress();button(text("reset_section")).onPress();server(p->check(BalanceConfig.MANA_MAX.get()==175,"Section reset must remain a draft before Apply"));apply();next(23);
        }else if(stage==23 && ++ticks==25){
            server(p->check(BalanceConfig.MANA_MAX.get().equals(BalanceConfig.MANA_MAX.getDefault())&&BalanceConfig.MANA_BASE.get().equals(BalanceConfig.MANA_BASE.getDefault()),"Section reset failed to apply defaults"));LOG.info("SETTINGS_UI_LIST_AND_RESETS_OK");PacketDistributor.sendToServer(new MagicNetwork.SettingsUpdate(200,new com.google.gson.Gson().toJson(initial)));next(14);
        }else if(stage==14 && ++ticks==30){
            server(p->initial.forEach((key,value)->check(Objects.equals(ConfigCommand.values().get(key).get(),value),"Restore failed for "+key)));LOG.info("SETTINGS_UI_APPLICATION_AUDIT_OK: real UI, network, server mechanics, cancellation, validation, synchronization and restart persistence verified");disconnect(15);
        }else if(stage==15 && mc.getSingleplayerServer()==null && ++ticks>20)mc.stop();
    }
}
