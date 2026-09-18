package com.restor.magicka;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MagicNetwork {
    public record Visual(int kind,int formula,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,float radius,int ticks) implements CustomPacketPayload {
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.kind);b.writeVarInt(v.formula);b.writeDouble(v.from.x);b.writeDouble(v.from.y);b.writeDouble(v.from.z);b.writeDouble(v.to.x);b.writeDouble(v.to.y);b.writeDouble(v.to.z);b.writeFloat(v.radius);b.writeVarInt(v.ticks);},
                b->new Visual(b.readVarInt(),b.readVarInt(),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat(),b.readVarInt()));
        @Override public Type<Visual> type(){return TYPE;}
    }
    public static java.util.function.Consumer<Visual> visualReceiver=v->{};
    public record QuickFill(int formula) implements CustomPacketPayload {
        public static final Type<QuickFill> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"quick_fill"));
        public static final StreamCodec<RegistryFriendlyByteBuf,QuickFill> CODEC=StreamCodec.of((b,v)->b.writeVarInt(v.formula),b->new QuickFill(b.readVarInt()));
        @Override public Type<QuickFill> type(){return TYPE;}
    }
    public record QueueState(int formula,int elapsed) implements CustomPacketPayload {
        public static final Type<QueueState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"queue"));
        public static final StreamCodec<RegistryFriendlyByteBuf,QueueState> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.formula);b.writeVarInt(v.elapsed);},b->new QueueState(b.readVarInt(),b.readVarInt()));
        @Override public Type<QueueState> type(){return TYPE;}
    }
    public static QueueState clientQueue=new QueueState(0,0);
    public record Construction(java.util.UUID id,int placed,int remaining) implements CustomPacketPayload {
        public static final Type<Construction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"construction"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Construction> CODEC=StreamCodec.of((b,v)->{b.writeUUID(v.id);b.writeVarInt(v.placed);b.writeVarInt(v.remaining);},b->new Construction(b.readUUID(),b.readVarInt(),b.readVarInt()));
        @Override public Type<Construction> type(){return TYPE;}
    }
    public static Construction clientConstruction;
    public record Catalog(boolean reset, boolean last, java.util.List<String> entries) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Catalog> CODEC = StreamCodec.of((b,v)->{
            b.writeBoolean(v.reset);b.writeBoolean(v.last);b.writeVarInt(v.entries.size());for(String s:v.entries)b.writeUtf(s,65536);
        }, b->{boolean reset=b.readBoolean(),last=b.readBoolean();int count=b.readVarInt();if(count<0 || count>12)throw new IllegalArgumentException("Catalog chunk too large");var entries=new java.util.ArrayList<String>();for(int i=0;i<count;i++)entries.add(b.readUtf(65536));return new Catalog(reset,last,entries);});
        @Override public Type<Catalog> type(){return TYPE;}
    }
    public record Shape(int entity, String shape) implements CustomPacketPayload {
        public static final Type<Shape> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"shape"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Shape> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.entity);b.writeUtf(v.shape,64);},b->new Shape(b.readVarInt(),b.readUtf(64)));
        @Override public Type<Shape> type(){return TYPE;}
    }
    public static final java.util.Map<Integer,String> clientShapes=new java.util.HashMap<>();
    public record Statuses(int entity, int mask) implements CustomPacketPayload {
        public static final Type<Statuses> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"statuses"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Statuses> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.entity);b.writeVarInt(v.mask);},b->new Statuses(b.readVarInt(),b.readVarInt()));
        @Override public Type<Statuses> type(){return TYPE;}
    }
    public record Stats(int[] values) implements CustomPacketPayload {
        public static final Type<Stats> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"stats"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Stats> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.values.length);for(int x:v.values)b.writeVarInt(x);},
                b->{int n=b.readVarInt();if(n<0 || n>64)throw new IllegalArgumentException("Too many stats");int[] v=new int[n];for(int i=0;i<n;i++)v[i]=b.readVarInt();return new Stats(v);});
        @Override public Type<Stats> type(){return TYPE;}
    }
    public record ShapeGrid(long low, long high) implements CustomPacketPayload {
        public static final Type<ShapeGrid> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"shape_grid"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ShapeGrid> CODEC=StreamCodec.of((b,v)->{b.writeLong(v.low);b.writeLong(v.high);},b->new ShapeGrid(b.readLong(),b.readLong()));
        @Override public Type<ShapeGrid> type(){return TYPE;}
    }
    public static long[] clientPattern=ShapePattern.defaults();
    public record SetShapeGrid(long low, long high) implements CustomPacketPayload {
        public static final Type<SetShapeGrid> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"set_shape_grid"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetShapeGrid> CODEC=StreamCodec.of((b,v)->{b.writeLong(v.low);b.writeLong(v.high);},b->new SetShapeGrid(b.readLong(),b.readLong()));
        @Override public Type<SetShapeGrid> type(){return TYPE;}
    }
    public record SetStat(int index, int value) implements CustomPacketPayload {
        public static final Type<SetStat> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID,"set_stat"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetStat> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.index);b.writeVarInt(v.value);},b->new SetStat(b.readVarInt(),b.readVarInt()));
        @Override public Type<SetStat> type(){return TYPE;}
    }
    public static int[] clientStats=MagicStats.defaults();
    private static final java.util.List<String> catalogBuffer=new java.util.ArrayList<>();
    public static final java.util.Map<Integer,Integer> clientStatuses=new java.util.HashMap<>();
    public static void clearClient(){clientState=new State(false,0,0,0,0,0,false,false);clientQueue=new QueueState(0,0);catalogBuffer.clear();clientStatuses.clear();clientShapes.clear();clientConstruction=null;clientStats=MagicStats.defaults();clientPattern=ShapePattern.defaults();RecipeRegistry.resetClient();}
    public static void construction(ServerPlayer player, java.util.UUID id, int placed, int remaining){
        Formula formula=Formula.unpack(CastingServer.snapshot(player).formula());
        if(!formula.empty()) CastingServer.construction(player,Math.max(0,MagicStats.resolve(player,formula.key()).budget()-remaining));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new Construction(id,placed,remaining));
    }
    // One bounded command, never client-provided world coordinates or block counts.
    public record Input(int action) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID, "input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeVarInt(value.action), buf -> new Input(buf.readVarInt()));
        @Override public Type<Input> type() { return TYPE; }
    }
    public record State(boolean enabled, int formula, int remaining, int total, int intent, int budget, boolean instant, boolean channeling) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Magicka.ID, "state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buf, v) -> { buf.writeBoolean(v.enabled); buf.writeVarInt(v.formula); buf.writeVarInt(v.remaining); buf.writeVarInt(v.total); buf.writeVarInt(v.intent); buf.writeVarInt(v.budget); buf.writeBoolean(v.instant); buf.writeBoolean(v.channeling); },
                buf -> new State(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<State> type() { return TYPE; }
    }
    /** Contains no client classes, so the payload handler is safe on dedicated servers. */
    public static State clientState = new State(false, 0, 0, 0, 0, 0, false, false);
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("10");
        registrar.playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->visualReceiver.accept(packet));
        registrar.playToServer(QuickFill.TYPE,QuickFill.CODEC,(packet,context)->{if(context.player() instanceof ServerPlayer p)CastingServer.quickFill(p,packet.formula);});
        registrar.playToClient(QueueState.TYPE,QueueState.CODEC,(packet,context)->clientQueue=packet);
        registrar.playToServer(Input.TYPE, Input.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) CastingServer.input(player, packet.action());
        });
        registrar.playToClient(State.TYPE, State.CODEC, (packet, context) -> clientState = packet);
        registrar.playToClient(Construction.TYPE,Construction.CODEC,(packet,context)->clientConstruction=packet);
        registrar.playToClient(Stats.TYPE,Stats.CODEC,(packet,context)->{int[] v=MagicStats.defaults();System.arraycopy(packet.values,0,v,0,Math.min(v.length,packet.values.length));clientStats=v;});
        registrar.playToClient(ShapeGrid.TYPE,ShapeGrid.CODEC,(packet,context)->clientPattern=ShapePattern.sanitize(packet.low,packet.high));
        registrar.playToServer(SetShapeGrid.TYPE,SetShapeGrid.CODEC,(packet,context)->{if(context.player() instanceof ServerPlayer p)ShapePattern.store(p,packet.low,packet.high);});
        registrar.playToServer(SetStat.TYPE,SetStat.CODEC,(packet,context)->{if(context.player() instanceof ServerPlayer p){if(packet.index<0)MagicStats.reset(p);else MagicStats.set(p,packet.index,packet.value);}});
        registrar.playToClient(Shape.TYPE,Shape.CODEC,(packet,context)->{if(packet.shape.isEmpty())clientShapes.remove(packet.entity);else clientShapes.put(packet.entity,packet.shape);});
        registrar.playToClient(Statuses.TYPE,Statuses.CODEC,(packet,context)->{if(packet.mask==0)clientStatuses.remove(packet.entity);else clientStatuses.put(packet.entity,packet.mask);});
        registrar.playToClient(Catalog.TYPE,Catalog.CODEC,(packet,context)->{
            if(packet.reset)catalogBuffer.clear();catalogBuffer.addAll(packet.entries);
            if(catalogBuffer.size()>RecipeRegistry.CATALOG_SIZE+256)throw new IllegalArgumentException("Catalog too large");
            if(packet.last){RecipeRegistry.acceptClient(catalogBuffer);catalogBuffer.clear();}
        });
    }
    private MagicNetwork() {}
}
