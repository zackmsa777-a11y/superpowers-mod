package com.superpowers.registry;

import com.superpowers.Superpowers;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Custom synthesized sounds (assets/superpowers/sounds/*.ogg). */
public final class ModSounds {
	private ModSounds() {}

	public static final SoundEvent CHARGE = reg("charge");
	public static final SoundEvent BOOM = reg("boom");
	public static final SoundEvent IMPACT = reg("impact");
	public static final SoundEvent WHOOSH = reg("whoosh");
	public static final SoundEvent SLASH = reg("slash");
	public static final SoundEvent BEAM = reg("beam");
	public static final SoundEvent BLACKHOLE = reg("blackhole");
	public static final SoundEvent CHOIR = reg("choir");
	public static final SoundEvent DEMON = reg("demon");
	public static final SoundEvent ULT = reg("ult");
	public static final SoundEvent TELEPORT = reg("teleport");
	public static final SoundEvent SELECT = reg("select");
	public static final SoundEvent HOLY = reg("holy");
	public static final SoundEvent FIRE = reg("fire");
	public static final SoundEvent THUNDER = reg("thunder");
	public static final SoundEvent TIMESTOP = reg("timestop");
	public static final SoundEvent SCREAM = reg("scream");
	public static final SoundEvent WHISPER = reg("whisper");
	public static final SoundEvent CRUNCH = reg("crunch");
	public static final SoundEvent HEARTBEAT = reg("heartbeat");
	public static final SoundEvent STATIC = reg("static");
	public static final SoundEvent DRONE = reg("drone");

	private static SoundEvent reg(String name) {
		ResourceLocation id = Superpowers.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {}
}
