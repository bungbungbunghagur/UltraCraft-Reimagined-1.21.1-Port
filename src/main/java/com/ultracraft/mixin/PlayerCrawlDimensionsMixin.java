package com.ultracraft.mixin;

import com.ultracraft.weapon.ServerTune;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class PlayerCrawlDimensionsMixin {
	@Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
	private void ultracraft$crawlBox(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof PlayerEntity player && pose == EntityPose.SWIMMING && !player.isTouchingWater()
			&& ServerTune.get("move.slideCrawl", 1) != 0)
			cir.setReturnValue(EntityDimensions.changing(0.6F, (float) ServerTune.get("move.crawlHeight", 0.5)));
	}
}
