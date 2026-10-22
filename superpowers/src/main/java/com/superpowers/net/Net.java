package com.superpowers.net;

import com.superpowers.Superpowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** All custom packets. */
public final class Net {
	private Net() {}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String name) {
		return new CustomPacketPayload.Type<>(Superpowers.id(name));
	}

	// ------------------------------------------------------------------ client -> server

	/** Use an ability slot (0 = M1, 1..4 = Z X C V). */
	public record Ability(int slot) implements CustomPacketPayload {
		public static final Type<Ability> TYPE = typeOf("ability");
		public static final StreamCodec<ByteBuf, Ability> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ability::slot, Ability::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Pick a power from the book (ordinal of PowerType, 0 = remove). */
	public record SelectPower(int power) implements CustomPacketPayload {
		public static final Type<SelectPower> TYPE = typeOf("select_power");
		public static final StreamCodec<ByteBuf, SelectPower> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectPower::power, SelectPower::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** 1 = awaken (G), 0 = end awakening (F). */
	public record Awaken(int action) implements CustomPacketPayload {
		public static final Type<Awaken> TYPE = typeOf("awaken");
		public static final StreamCodec<ByteBuf, Awaken> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Awaken::action, Awaken::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ------------------------------------------------------------------ server -> client

	/** Local player's power, mana, cooldowns, awakening meter and awakening time left (-1 = infinite). */
	public record Sync(int power, float mana, boolean infinite, int[] cooldowns, float meter, int awakenLeft) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = typeOf("sync");
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(Sync::write, Sync::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(power);
			buf.writeFloat(mana);
			buf.writeBoolean(infinite);
			for (int i = 0; i < 5; i++) buf.writeVarInt(cooldowns[i]);
			buf.writeFloat(meter);
			buf.writeInt(awakenLeft);
		}

		private static Sync read(RegistryFriendlyByteBuf buf) {
			int p = buf.readVarInt();
			float m = buf.readFloat();
			boolean inf = buf.readBoolean();
			int[] cd = new int[5];
			for (int i = 0; i < 5; i++) cd[i] = buf.readVarInt();
			return new Sync(p, m, inf, cd, buf.readFloat(), buf.readInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Which power some player has and whether they are awakened (for auras on other players). */
	public record PowerOf(int entityId, int power, boolean awakened, boolean flying) implements CustomPacketPayload {
		public static final Type<PowerOf> TYPE = typeOf("power_of");
		public static final StreamCodec<ByteBuf, PowerOf> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, PowerOf::entityId, ByteBufCodecs.VAR_INT, PowerOf::power, ByteBufCodecs.BOOL, PowerOf::awakened,
				ByteBufCodecs.BOOL, PowerOf::flying, PowerOf::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A visual effect. Meaning of dir/scale/extra depends on the effect id (see Fx). flags bit 0 = awakened caster. */
	public record Effect(int fx, double x, double y, double z, float dx, float dy, float dz, int caster, float scale, int extra, int flags)
			implements CustomPacketPayload {
		public static final Type<Effect> TYPE = typeOf("fx");
		public static final StreamCodec<RegistryFriendlyByteBuf, Effect> CODEC = CustomPacketPayload.codec(Effect::write, Effect::read);

		public boolean awakened() {
			return (flags & 1) != 0;
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(fx);
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			buf.writeFloat(dx);
			buf.writeFloat(dy);
			buf.writeFloat(dz);
			buf.writeVarInt(caster);
			buf.writeFloat(scale);
			buf.writeVarInt(extra);
			buf.writeVarInt(flags);
		}

		private static Effect read(RegistryFriendlyByteBuf buf) {
			return new Effect(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Play a body animation on a player. */
	public record Anim(int entityId, int anim, int duration) implements CustomPacketPayload {
		public static final Type<Anim> TYPE = typeOf("anim");
		public static final StreamCodec<ByteBuf, Anim> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Anim::entityId, ByteBufCodecs.VAR_INT, Anim::anim, ByteBufCodecs.VAR_INT, Anim::duration, Anim::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Start a cutscene on the caster's client. kind 0 = ultimate, 1 = awakening. */
	public record Cutscene(int power, double ax, double ay, double az, float yaw, double tx, double ty, double tz, int kind)
			implements CustomPacketPayload {
		public static final Type<Cutscene> TYPE = typeOf("cutscene");
		public static final StreamCodec<RegistryFriendlyByteBuf, Cutscene> CODEC = CustomPacketPayload.codec(Cutscene::write, Cutscene::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(power);
			buf.writeDouble(ax);
			buf.writeDouble(ay);
			buf.writeDouble(az);
			buf.writeFloat(yaw);
			buf.writeDouble(tx);
			buf.writeDouble(ty);
			buf.writeDouble(tz);
			buf.writeVarInt(kind);
		}

		private static Cutscene read(RegistryFriendlyByteBuf buf) {
			return new Cutscene(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Chunks of destroyed blocks to fling around (block state ids + offsets from the centre). */
	public record Debris(double x, double y, double z, float power, int[] states, float[] offsets) implements CustomPacketPayload {
		public static final Type<Debris> TYPE = typeOf("debris");
		public static final StreamCodec<RegistryFriendlyByteBuf, Debris> CODEC = CustomPacketPayload.codec(Debris::write, Debris::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeDouble(x);
			buf.writeDouble(y);
			buf.writeDouble(z);
			buf.writeFloat(power);
			buf.writeVarInt(states.length);
			for (int i = 0; i < states.length; i++) {
				buf.writeVarInt(states[i]);
				buf.writeFloat(offsets[i * 3]);
				buf.writeFloat(offsets[i * 3 + 1]);
				buf.writeFloat(offsets[i * 3 + 2]);
			}
		}

		private static Debris read(RegistryFriendlyByteBuf buf) {
			double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
			float power = buf.readFloat();
			int n = Math.min(256, buf.readVarInt());
			int[] st = new int[n];
			float[] off = new float[n * 3];
			for (int i = 0; i < n; i++) {
				st[i] = buf.readVarInt();
				off[i * 3] = buf.readFloat();
				off[i * 3 + 1] = buf.readFloat();
				off[i * 3 + 2] = buf.readFloat();
			}
			return new Debris(x, y, z, power, st, off);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Open the power book screen. */
	public record OpenBook(int unused) implements CustomPacketPayload {
		public static final Type<OpenBook> TYPE = typeOf("open_book");
		public static final StreamCodec<ByteBuf, OpenBook> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenBook::unused, OpenBook::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(Ability.TYPE, Ability.CODEC);
		PayloadTypeRegistry.playC2S().register(SelectPower.TYPE, SelectPower.CODEC);
		PayloadTypeRegistry.playC2S().register(Awaken.TYPE, Awaken.CODEC);
		PayloadTypeRegistry.playS2C().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.playS2C().register(PowerOf.TYPE, PowerOf.CODEC);
		PayloadTypeRegistry.playS2C().register(Effect.TYPE, Effect.CODEC);
		PayloadTypeRegistry.playS2C().register(Anim.TYPE, Anim.CODEC);
		PayloadTypeRegistry.playS2C().register(Cutscene.TYPE, Cutscene.CODEC);
		PayloadTypeRegistry.playS2C().register(Debris.TYPE, Debris.CODEC);
		PayloadTypeRegistry.playS2C().register(OpenBook.TYPE, OpenBook.CODEC);
	}
}
