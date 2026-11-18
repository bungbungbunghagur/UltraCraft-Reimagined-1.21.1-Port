package com.ultracraft.net;

import net.minecraft.network.PacketByteBuf;

/** Server → client: hide the ULTRAKILL overlay during The Broken Script's Integrity ending. */
public record TbsCutscenePayload(int ticks) {
	public void write(PacketByteBuf buf) { buf.writeVarInt(ticks); }
	public static TbsCutscenePayload read(PacketByteBuf buf) { return new TbsCutscenePayload(buf.readVarInt()); }
}
