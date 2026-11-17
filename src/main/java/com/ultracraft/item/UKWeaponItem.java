package com.ultracraft.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.function.Consumer;

/**
 * An ULTRAKILL weapon as a Minecraft item. NBT `var` = variation 0/1/2. Firing is handled by the client
 * weapon code and Fire packets, never by vanilla item use. The Revolver keeps its own class for history,
 * but reads through the same helpers.
 */
public class UKWeaponItem extends Item {
	public final WeaponKind kind;

	public UKWeaponItem(WeaponKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public static ItemStack of(WeaponKind kind, int variation) {
		ItemStack s = new ItemStack(UKItems.of(kind));
		updateCustomData(s, nbt -> nbt.putInt("var", variation));
		return s;
	}

	/** The weapon kind of a stack, or null when it is not an ULTRAKILL weapon. */
	public static WeaponKind kindOf(ItemStack stack) {
		if (stack.getItem() instanceof UKWeaponItem w) return w.kind;
		if (stack.getItem() instanceof RevolverItem) return WeaponKind.REVOLVER;
		return null;
	}

	public static ItemStack ofAlt(WeaponKind kind, int variation) {
		ItemStack s = of(kind, variation);
		updateCustomData(s, nbt -> nbt.putBoolean("alt", true));
		return s;
	}

	/** NBT `alt`: the slot's alternate weapon (Minos revolver, Jackhammer, Sawblade Launcher). */
	public static boolean alternate(ItemStack stack) {
		return customData(stack).getBoolean("alt");
	}

	public static int variation(ItemStack stack) {
		return Math.floorMod(customData(stack).getInt("var"), 3);
	}

	public static void setVariation(ItemStack stack, int v) {
		v = Math.floorMod(v, 3);
		int variation = v;
		boolean alt = alternateOf(stack, variation);
		updateCustomData(stack, nbt -> {
			nbt.putInt("var", variation);
			nbt.putBoolean("alt", alt);
		});
	}

	/**
	 * Standard or alternate per variation (the shop's equipment buttons): NBT `altVars`, a bit per variation; `alt` is
	 * the held variation's. A stack from before the shop (no `altVars`) has its `alt` on every variation.
	 */
	public static boolean alternateOf(ItemStack stack, int variation) {
		NbtCompound nbt = customData(stack);
		if (!nbt.contains("altVars")) return nbt.getBoolean("alt");
		return (nbt.getInt("altVars") >> Math.floorMod(variation, 3) & 1) != 0;
	}

	public static void setAlternateOf(ItemStack stack, int variation, boolean alt) {
		int mask = 0;
		for (int v = 0; v < 3; v++) if (v == variation ? alt : alternateOf(stack, v)) mask |= 1 << v;
		int currentVariation = UKWeaponItem.variation(stack);
		int altMask = mask;
		updateCustomData(stack, nbt -> {
			nbt.putInt("altVars", altMask);
			if (currentVariation == Math.floorMod(variation, 3)) nbt.putBoolean("alt", alt);
		});
	}

	private static NbtCompound customData(ItemStack stack) {
		NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
		return component == null ? new NbtCompound() : component.copyNbt();
	}

	private static void updateCustomData(ItemStack stack, Consumer<NbtCompound> update) {
		NbtCompound nbt = customData(stack);
		update.accept(nbt);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.translatable("item.ultracraft." + kind.rig(alternate(stack)) + "." + variation(stack));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return TypedActionResult.pass(user.getStackInHand(hand));
	}
}
