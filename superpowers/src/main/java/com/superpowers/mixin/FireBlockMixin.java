package com.superpowers.mixin;

import com.superpowers.power.Awakening;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks consumed by fire feed nearby Fire users' awakening meters. */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Inject(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
	private void superpowers$burnedAway(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (level instanceof ServerLevel sl) Awakening.onBlockBurned(sl, pos);
	}

	@Inject(method = "checkBurnOut", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
	private void superpowers$burnedToFire(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (level instanceof ServerLevel sl) Awakening.onBlockBurned(sl, pos);
	}
}
