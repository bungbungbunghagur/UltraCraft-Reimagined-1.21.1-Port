package com.ultracraft.mixin;

import com.ultracraft.weapon.ServerTune;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class PlayerCrawlEyeMixin {
	@Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
	private void ultracraft$crawlEye(EntityPose pose, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof PlayerEntity player && pose == EntityPose.SWIMMING && !player.isTouchingWater()
			&& ServerTune.get("move.slideCrawl", 1) != 0)
			cir.setReturnValue((float) ServerTune.get("move.crawlEye", 0.42));
	}
}
