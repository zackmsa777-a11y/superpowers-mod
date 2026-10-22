package com.superpowers.registry;

import com.superpowers.Superpowers;
import com.superpowers.entity.PowerProjectile;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private ModEntities() {}

	public static final EntityType<PowerProjectile> PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			Superpowers.id("power_projectile"),
			EntityType.Builder.<PowerProjectile>of(PowerProjectile::new, MobCategory.MISC)
					.sized(0.6f, 0.6f)
					.clientTrackingRange(10)
					.updateInterval(1)
					.build("power_projectile"));

	public static void init() {}
}
