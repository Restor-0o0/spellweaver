package com.restor.magicka;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MagicNetwork {
    public record Visual(int kind,int formula,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,float radius,int ticks) implements Packet {
        public static final Codec<Visual> CODEC=Codec.of((b,v)->{b.writeVarInt(v.kind);b.writeVarInt(v.formula);b.writeDouble(v.from.x);b.writeDouble(v.from.y);b.writeDouble(v.from.z);b.writeDouble(v.to.x);b.writeDouble(v.to.y);b.writeDouble(v.to.z);b.writeFloat(v.radius);b.writeVarInt(v.ticks);},
                b->new Visual(b.readVarInt(),b.readVarInt(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt()));
    }
    public static java.util.function.Consumer<Visual> visualReceiver=v->{};
    public record QuickFill(int formula) implements Packet {
        public static final Codec<QuickFill> CODEC=Codec.of((b,v)->b.writeVarInt(v.formula),b->new QuickFill(b.readVarInt()));
    }
    public record QueueState(int formula,int elapsed) implements Packet {
        public static final Codec<QueueState> CODEC=Codec.of((b,v)->{b.writeVarInt(v.formula);b.writeVarInt(v.elapsed);},b->new QueueState(b.readVarInt(),b.readVarInt()));
    }
    public static QueueState clientQueue=new QueueState(0,0);
    public record Construction(java.util.UUID id,int placed,int remaining) implements Packet {
        public static final Codec<Construction> CODEC=Codec.of((b,v)->{b.writeUUID(v.id);b.writeVarInt(v.placed);b.writeVarInt(v.remaining);},b->new Construction(b.readUUID(),b.readVarInt(),b.readVarInt()));
    }
    public static Construction clientConstruction;
    public record Catalog(boolean reset, boolean last, java.util.List<String> entries) implements Packet {
        public static final Codec<Catalog> CODEC = Codec.of((b,v)->{
            b.writeBoolean(v.reset);b.writeBoolean(v.last);b.writeVarInt(v.entries.size());for(String s:v.entries)b.writeUtf(s,65536);
        }, b->{boolean reset=b.readBoolean(),last=b.readBoolean();int count=b.readVarInt();if(count<0 || count>12)throw new IllegalArgumentException("Catalog chunk too large");var entries=new java.util.ArrayList<String>();for(int i=0;i<count;i++)entries.add(b.readUtf(65536));return new Catalog(reset,last,entries);});
    }
    public record Shape(int entity, String shape) implements Packet {
        public static final Codec<Shape> CODEC=Codec.of((b,v)->{b.writeVarInt(v.entity);b.writeUtf(v.shape,64);},b->new Shape(b.readVarInt(),b.readUtf(64)));
    }
    public static final java.util.Map<Integer,String> clientShapes=new java.util.HashMap<>();
    public record Locks(int mask,int ticks) implements Packet {
        public static final Codec<Locks> CODEC=Codec.of((b,v)->{b.writeVarInt(v.mask);b.writeVarInt(v.ticks);},b->new Locks(b.readVarInt(),b.readVarInt()));
    }
    public static int clientLocks;
    public static long clientLocksUntil;
    public record Statuses(int entity, int mask) implements Packet {
        public static final Codec<Statuses> CODEC=Codec.of((b,v)->{b.writeVarInt(v.entity);b.writeVarInt(v.mask);},b->new Statuses(b.readVarInt(),b.readVarInt()));
    }
    public record Stats(int[] values,int[] gear) implements Packet {
        public static final Codec<Stats> CODEC=Codec.of((b,v)->{writeRow(b,v.values);writeRow(b,v.gear);},
                b->new Stats(readRow(b),readRow(b)));
        private static void writeRow(FriendlyByteBuf b,int[] row){b.writeVarInt(row.length);for(int x:row)b.writeVarInt(x+10000);}
        private static int[] readRow(FriendlyByteBuf b){
            int n=b.readVarInt();if(n<0 || n>64)throw new IllegalArgumentException("Too many stats");
            int[] row=new int[n];for(int i=0;i<n;i++)row[i]=b.readVarInt()-10000;return row;
        }
    }
    public record ShapeGrid(long low, long high) implements Packet {
        public static final Codec<ShapeGrid> CODEC=Codec.of((b,v)->{b.writeLong(v.low);b.writeLong(v.high);},b->new ShapeGrid(b.readLong(),b.readLong()));
    }
    public static long[] clientPattern=ShapePattern.defaults();
    public record SetShapeGrid(long low, long high) implements Packet {
        public static final Codec<SetShapeGrid> CODEC=Codec.of((b,v)->{b.writeLong(v.low);b.writeLong(v.high);},b->new SetShapeGrid(b.readLong(),b.readLong()));
    }
    public record SetStat(int index, int value) implements Packet {
        public static final Codec<SetStat> CODEC=Codec.of((b,v)->{b.writeVarInt(v.index);b.writeVarInt(v.value);},b->new SetStat(b.readVarInt(),b.readVarInt()));
    }
    public static int[] clientStats=MagicStats.defaults();
    public static int[] clientGear=new int[MagicStats.COUNT];
    private static final java.util.List<String> catalogBuffer=new java.util.ArrayList<>();
    public static final java.util.Map<Integer,Integer> clientStatuses=new java.util.HashMap<>();
    public static void clearClient(){clientState=new State(false,0,0,0,0,0,false,false);clientQueue=new QueueState(0,0);catalogBuffer.clear();clientStatuses.clear();clientShapes.clear();clientConstruction=null;clientLocks=0;clientLocksUntil=0;clientStats=MagicStats.defaults();clientGear=new int[MagicStats.COUNT];clientPattern=ShapePattern.defaults();RecipeRegistry.resetClient();}
    public static void construction(ServerPlayer player, java.util.UUID id, int placed, int remaining){
        Formula formula=Formula.unpack(CastingServer.snapshot(player).formula());
        if(!formula.empty()) CastingServer.construction(player,Math.max(0,MagicStats.resolve(player,formula.key()).budget()-remaining));
        sendToPlayer(player,new Construction(id,placed,remaining));
    }
    // One bounded command, never client-provided world coordinates or block counts.
    public record Input(int action) implements Packet {
        public static final Codec<Input> CODEC = Codec.of(
                (buf, value) -> buf.writeVarInt(value.action), buf -> new Input(buf.readVarInt()));
    }
    public record State(boolean enabled, int formula, int remaining, int total, int intent, int budget, boolean instant, boolean channeling) implements Packet {
        public static final Codec<State> CODEC = Codec.of(
                (buf, v) -> { buf.writeBoolean(v.enabled); buf.writeVarInt(v.formula); buf.writeVarInt(v.remaining); buf.writeVarInt(v.total); buf.writeVarInt(v.intent); buf.writeVarInt(v.budget); buf.writeBoolean(v.instant); buf.writeBoolean(v.channeling); },
                buf -> new State(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
    }
    /** Contains no client classes, so the payload handler is safe on dedicated servers. */
    public static State clientState = new State(false, 0, 0, 0, 0, 0, false, false);
    public static void register() {
        toClient(Visual.class,Visual.CODEC,packet->visualReceiver.accept(packet));
        toServer(QuickFill.class,QuickFill.CODEC,(packet,player)->CastingServer.quickFill(player,packet.formula));
        toClient(Locks.class,Locks.CODEC,packet->{clientLocks=packet.mask;clientLocksUntil=System.currentTimeMillis()+packet.ticks*50L;});
        toClient(QueueState.class,QueueState.CODEC,packet->clientQueue=packet);
        toServer(Input.class,Input.CODEC,(packet,player)->{
            CastingServer.input(player, packet.action());
        });
        toClient(State.class,State.CODEC,packet->clientState = packet);
        toClient(Construction.class,Construction.CODEC,packet->clientConstruction=packet);
        toClient(Stats.class,Stats.CODEC,packet->{
            int[] v=MagicStats.defaults();System.arraycopy(packet.values,0,v,0,Math.min(v.length,packet.values.length));clientStats=v;
            int[] g=new int[MagicStats.COUNT];System.arraycopy(packet.gear,0,g,0,Math.min(g.length,packet.gear.length));clientGear=g;
        });
        toClient(ShapeGrid.class,ShapeGrid.CODEC,packet->clientPattern=ShapePattern.sanitize(packet.low,packet.high));
        toServer(SetShapeGrid.class,SetShapeGrid.CODEC,(packet,player)->ShapePattern.store(player,packet.low,packet.high));
        toServer(SetStat.class,SetStat.CODEC,(packet,player)->{if(packet.index<0)MagicStats.reset(player);else MagicStats.set(player,packet.index,packet.value);});
        toClient(Shape.class,Shape.CODEC,packet->{if(packet.shape.isEmpty())clientShapes.remove(packet.entity);else clientShapes.put(packet.entity,packet.shape);});
        toClient(Statuses.class,Statuses.CODEC,packet->{if(packet.mask==0)clientStatuses.remove(packet.entity);else clientStatuses.put(packet.entity,packet.mask);});
        toClient(Catalog.class,Catalog.CODEC,packet->{
            if(packet.reset)catalogBuffer.clear();catalogBuffer.addAll(packet.entries);
            if(catalogBuffer.size()>RecipeRegistry.CATALOG_SIZE+256)throw new IllegalArgumentException("Catalog too large");
            if(packet.last){RecipeRegistry.acceptClient(catalogBuffer);catalogBuffer.clear();}
        });
    }
    public interface Packet {}
    public record Codec<T>(BiConsumer<FriendlyByteBuf,T> writer, Function<FriendlyByteBuf,T> reader) {
        public static <T> Codec<T> of(BiConsumer<FriendlyByteBuf,T> writer, Function<FriendlyByteBuf,T> reader) { return new Codec<>(writer,reader); }
    }
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Magicka.ID,"main"),()->"11","11"::equals,"11"::equals);
    private static int nextId;
    private static <T extends Packet> void toClient(Class<T> type, Codec<T> codec, Consumer<T> handler) {
        CHANNEL.messageBuilder(type,nextId++,NetworkDirection.PLAY_TO_CLIENT).encoder((T v,FriendlyByteBuf b)->codec.writer().accept(b,v)).decoder(codec.reader())
                .consumerMainThread((T v,Supplier<NetworkEvent.Context> c)->handler.accept(v)).add();
    }
    private static <T extends Packet> void toServer(Class<T> type, Codec<T> codec, BiConsumer<T,ServerPlayer> handler) {
        CHANNEL.messageBuilder(type,nextId++,NetworkDirection.PLAY_TO_SERVER).encoder((T v,FriendlyByteBuf b)->codec.writer().accept(b,v)).decoder(codec.reader())
                .consumerMainThread((T v,Supplier<NetworkEvent.Context> c)->{ServerPlayer player=c.get().getSender();if(player!=null)handler.accept(v,player);}).add();
    }
    public static void sendToPlayer(ServerPlayer player, Packet packet) { CHANNEL.send(PacketDistributor.PLAYER.with(()->player),packet); }
    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, Packet packet) { CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->entity),packet); }
    public static void sendToServer(Packet packet) { CHANNEL.sendToServer(packet); }
    private MagicNetwork() {}
}
