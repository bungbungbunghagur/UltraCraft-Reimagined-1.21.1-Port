package com.ultracraft.client;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/** Detects The Broken Script's Integrity Phase 3 ending without a hard dependency on that optional mod. */
public final class TbsCutscene {
	private static World scannedWorld;
	private static long scannedTick = Long.MIN_VALUE;
	private static long hideUntil = Long.MIN_VALUE;
	private static boolean cached;
	private static volatile long forcedHideUntilMillis;

	private TbsCutscene() {}

	public static void hideForTicks(int ticks) {
		forcedHideUntilMillis = Math.max(forcedHideUntilMillis, System.currentTimeMillis() + Math.max(1, ticks) * 50L);
	}

	public static boolean hideUltrakillOverlay(ClientPlayerEntity player) {
		if (System.currentTimeMillis() < forcedHideUntilMillis) return true;
		if (player == null || player.getWorld() == null) return false;
		World world = player.getWorld();
		long tick = world.getTime();
		if (world != scannedWorld) {
			scannedWorld = world;
			scannedTick = Long.MIN_VALUE;
			hideUntil = Long.MIN_VALUE;
			cached = false;
		}
		if (tick == scannedTick) return cached;
		scannedTick = tick;
		cached = tick <= hideUntil;
		if (cached) return true;

		Box nearby = player.getBoundingBox().expand(512);
		for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, nearby, TbsCutscene::isIntegrityEnding)) {
			hideUntil = tick + 400; // keep the overlays hidden through the ending after the entity is removed
			cached = true;
			return true;
		}
		return false;
	}

	private static boolean isIntegrityEnding(LivingEntity entity) {
		String type = entity.getClass().getName().toLowerCase(Locale.ROOT);
		String path = net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).getPath().toLowerCase(Locale.ROOT);
		boolean integrity = type.contains("integrity") || path.contains("integrity");
		boolean phase3 = type.contains("phase3") || type.contains("phase_3") || type.contains("phase 3")
			|| path.contains("phase3") || path.contains("phase_3") || path.contains("phase 3");
		if (!integrity || !phase3) return false;
		if (!entity.isAlive()) return true;
		for (Method method : entity.getClass().getMethods()) {
			String name = method.getName().toLowerCase(Locale.ROOT);
			if (method.getParameterCount() != 0 || !(name.contains("state") || name.contains("animation") || name.contains("ending") || name.contains("cutscene") || name.contains("defeat"))) continue;
			try {
				Object value = method.invoke(entity);
				if (value instanceof Boolean flag && flag && (name.contains("ending") || name.contains("cutscene") || name.contains("defeat"))) return true;
				if (looksLikeEnding(value)) return true;
			} catch (ReflectiveOperationException | RuntimeException ignored) {}
		}
		for (Field field : entity.getClass().getDeclaredFields()) {
			String name = field.getName().toLowerCase(Locale.ROOT);
			if (!(name.contains("state") || name.contains("animation") || name.contains("ending") || name.contains("cutscene") || name.contains("defeat"))) continue;
			try {
				field.setAccessible(true);
				Object value = field.get(entity);
				if (value instanceof Boolean flag && flag && (name.contains("ending") || name.contains("cutscene") || name.contains("defeat"))) return true;
				if (looksLikeEnding(value)) return true;
			} catch (ReflectiveOperationException | RuntimeException ignored) {}
		}
		return false;
	}

	private static boolean looksLikeEnding(Object value) {
		if (value == null) return false;
		String state = value.toString().toUpperCase(Locale.ROOT);
		return state.contains("DEATH") || state.contains("DYING") || state.contains("DEFEAT") || state.contains("ENDING") || state.contains("CUTSCENE");
	}
}
