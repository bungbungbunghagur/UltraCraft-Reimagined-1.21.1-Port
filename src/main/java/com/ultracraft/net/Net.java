package com.ultracraft.net;

import com.ultracraft.UltraCraft;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Network payload dispatch for the mod's small typed messages. */
public final class Net {
	private static final String VERSION = "1";
	private static final Map<Class<?>, MessageType<?>> types = new HashMap<>();
	private static final Map<Integer, MessageType<?>> typesById = new HashMap<>();
	private static final Map<Class<?>, BiConsumer<Object, ServerPlayerEntity>> serverHandlers = new HashMap<>();
	private static final Map<Class<?>, Consumer<Object>> clientHandlers = new HashMap<>();
	private static int nextId;

	private record NetworkPayload(CustomPayload.Id<NetworkPayload> payloadType, int id, byte[] data) implements CustomPayload {
		private static final CustomPayload.Id<NetworkPayload> SERVERBOUND_TYPE =
			new CustomPayload.Id<>(Identifier.of(UltraCraft.MOD_ID, "serverbound"));
		private static final CustomPayload.Id<NetworkPayload> CLIENTBOUND_TYPE =
			new CustomPayload.Id<>(Identifier.of(UltraCraft.MOD_ID, "clientbound"));
		private static final PacketCodec<RegistryByteBuf, NetworkPayload> SERVERBOUND_CODEC = codec(SERVERBOUND_TYPE);
		private static final PacketCodec<RegistryByteBuf, NetworkPayload> CLIENTBOUND_CODEC = codec(CLIENTBOUND_TYPE);

		private static PacketCodec<RegistryByteBuf, NetworkPayload> codec(CustomPayload.Id<NetworkPayload> type) {
			return PacketCodec.of(
				(payload, buf) -> {
					buf.writeVarInt(payload.id);
					buf.writeByteArray(payload.data);
				},
				buf -> new NetworkPayload(type, buf.readVarInt(), buf.readByteArray())
			);
		}

		@Override
		public CustomPayload.Id<? extends CustomPayload> getId() {
			return payloadType;
		}
	}

	private record MessageType<T>(int id, Class<T> type, BiConsumer<T, PacketByteBuf> write,
		Function<PacketByteBuf, T> read, boolean serverbound) {}

	private Net() {}

	public static void register(RegisterPayloadHandlersEvent event) {
		toServerType(FirePayload.class, FirePayload::write, FirePayload::read);
		toClientType(BeamPayload.class, BeamPayload::write, BeamPayload::read);
		toClientType(HitPayload.class, HitPayload::write, HitPayload::read);
		toServerType(PointsPayload.class, PointsPayload::write, PointsPayload::read);
		toClientType(PointsSyncPayload.class, PointsSyncPayload::write, PointsSyncPayload::read);
		toClientType(ParryWarnPayload.class, ParryWarnPayload::write, ParryWarnPayload::read);
		toClientType(ExplosionPayload.class, ExplosionPayload::write, ExplosionPayload::read);
		toClientType(LaunchPayload.class, LaunchPayload::write, LaunchPayload::read);
		toClientType(GasolinePayload.class, GasolinePayload::write, GasolinePayload::read);
		toClientType(TbsCutscenePayload.class, TbsCutscenePayload::write, TbsCutscenePayload::read);
		toServerType(ShopPayload.class, ShopPayload::write, ShopPayload::read);
		toClientType(OwnedPayload.class, OwnedPayload::write, OwnedPayload::read);
		toServerType(HandPayload.class, HandPayload::write, HandPayload::read);

		PayloadRegistrar registrar = event.registrar(VERSION);
		registrar.playToServer(NetworkPayload.SERVERBOUND_TYPE, NetworkPayload.SERVERBOUND_CODEC, Net::handleServer);
		registrar.playToClient(NetworkPayload.CLIENTBOUND_TYPE, NetworkPayload.CLIENTBOUND_CODEC, Net::handleClient);
	}

	private static <T> void toServerType(Class<T> type, BiConsumer<T, PacketByteBuf> write, Function<PacketByteBuf, T> read) {
		registerType(type, write, read, true);
	}

	private static <T> void toClientType(Class<T> type, BiConsumer<T, PacketByteBuf> write, Function<PacketByteBuf, T> read) {
		registerType(type, write, read, false);
	}

	private static <T> void registerType(Class<T> type, BiConsumer<T, PacketByteBuf> write, Function<PacketByteBuf, T> read, boolean serverbound) {
		MessageType<T> messageType = new MessageType<>(nextId++, type, write, read, serverbound);
		types.put(type, messageType);
		typesById.put(messageType.id(), messageType);
	}

	private static void handleServer(NetworkPayload payload, IPayloadContext context) {
		MessageType<?> type = typesById.get(payload.id());
		if (type == null || !type.serverbound()) {
			context.disconnect(Text.literal("Invalid UltraCraft serverbound payload"));
			return;
		}
		Object message = decode(type, payload.data());
		ServerPlayerEntity sender = (ServerPlayerEntity) context.player();
		BiConsumer<Object, ServerPlayerEntity> handler = serverHandlers.get(type.type());
		if (sender == null) return;
		if (message instanceof HandPayload handPayload) {
			if (handPayload.off()) OFFHAND.add(sender.getUuid());
			else OFFHAND.remove(sender.getUuid());
		} else if (handler != null) {
			boolean swap = OFFHAND.contains(sender.getUuid());
			if (swap) swapHands(sender);
			try {
				handler.accept(message, sender);
			} finally {
				if (swap) swapHands(sender);
			}
		}
	}

	private static void handleClient(NetworkPayload payload, IPayloadContext context) {
		MessageType<?> type = typesById.get(payload.id());
		if (type == null || type.serverbound()) {
			context.disconnect(Text.literal("Invalid UltraCraft clientbound payload"));
			return;
		}
		Object message = decode(type, payload.data());
		Consumer<Object> handler = clientHandlers.get(type.type());
		if (handler != null) handler.accept(message);
	}

	private static <T> T decode(MessageType<T> type, byte[] data) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data));
		return type.read().apply(buf);
	}

	private static NetworkPayload encode(Object message, boolean serverbound) {
		MessageType<?> type = types.get(message.getClass());
		if (type == null) throw new IllegalArgumentException("Unregistered UltraCraft payload: " + message.getClass().getName());
		if (type.serverbound() != serverbound) throw new IllegalArgumentException("UltraCraft payload has the wrong direction: " + message.getClass().getName());
		return encode(type, message);
	}

	private static <T> NetworkPayload encode(MessageType<T> type, Object message) {
		PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
		type.write().accept(type.type().cast(message), buf);
		byte[] data = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), data);
		CustomPayload.Id<NetworkPayload> payloadType = type.serverbound() ? NetworkPayload.SERVERBOUND_TYPE : NetworkPayload.CLIENTBOUND_TYPE;
		return new NetworkPayload(payloadType, type.id(), data);
	}

	@SuppressWarnings("unchecked")
	public static <T> void onServer(Class<T> type, BiConsumer<T, ServerPlayerEntity> handler) {
		serverHandlers.put(type, (BiConsumer<Object, ServerPlayerEntity>) handler);
	}

	@SuppressWarnings("unchecked")
	public static <T> void onClient(Class<T> type, Consumer<T> handler) {
		clientHandlers.put(type, (Consumer<Object>) handler);
	}

	private static final java.util.Set<java.util.UUID> OFFHAND = new java.util.HashSet<>();

	public static boolean offhand(ServerPlayerEntity player) {
		return OFFHAND.contains(player.getUuid());
	}

	public static void clearOffhand(ServerPlayerEntity player) {
		OFFHAND.remove(player.getUuid());
	}

	public static void swapHands(net.minecraft.entity.player.PlayerEntity player) {
		var inventory = player.getInventory();
		int selected = inventory.selectedSlot;
		if (selected < 0 || selected >= inventory.main.size()) return;
		net.minecraft.item.ItemStack mainHand = inventory.main.get(selected);
		inventory.main.set(selected, inventory.offHand.get(0));
		inventory.offHand.set(0, mainHand);
	}

	private static boolean offhandMode, offhandSent;

	public static void beginOffhand() {
		offhandMode = true;
		offhandSent = false;
	}

	public static void endOffhand() {
		if (offhandSent) toServer(new HandPayload(false));
		offhandMode = false;
		offhandSent = false;
	}

	public static void toServer(Object message) {
		if (offhandMode && !offhandSent) {
			PacketDistributor.sendToServer(encode(new HandPayload(true), true));
			offhandSent = true;
		}
		PacketDistributor.sendToServer(encode(message, true));
	}

	public static void toPlayer(ServerPlayerEntity player, Object message) {
		PacketDistributor.sendToPlayer(player, encode(message, false));
	}

	/** Everyone tracking a point, including the player themselves, in a world. */
	public static void toNear(ServerWorld world, Vec3d pos, Object message) {
		PacketDistributor.sendToPlayersNear(world, null, pos.x, pos.y, pos.z, 128, encode(message, false));
	}

	public static void writeVec(PacketByteBuf buf, Vec3d vector) {
		buf.writeDouble(vector.x);
		buf.writeDouble(vector.y);
		buf.writeDouble(vector.z);
	}

	public static Vec3d readVec(PacketByteBuf buf) {
		return new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}
}
