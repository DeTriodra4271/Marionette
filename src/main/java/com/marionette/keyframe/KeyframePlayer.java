package com.marionette.keyframe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Plays a {@link KeyframeSequence} back by smoothly flying the player
 * through the recorded poses — a Catmull-Rom spline through position for
 * smooth curves, wrapped-angle interpolation for yaw so turns take the
 * short way round, and plain interpolation for pitch.
 *
 * <p>Unlike the macro {@code Player}, this deliberately drives position
 * directly rather than simulating input: a keyframe path is a virtual
 * camera rig, not a real physical walk, so there's nothing to be faithful
 * to except the curve itself.
 */
public class KeyframePlayer {
	private KeyframeSequence sequence;
	private boolean playing;
	private boolean loop;
	private int elapsedTicks;

	public void start(KeyframeSequence sequence, boolean loop) {
		this.sequence = sequence;
		this.elapsedTicks = 0;
		this.loop = loop;
		this.playing = sequence != null && !sequence.keyframes().isEmpty();
	}

	public void stop() {
		playing = false;
	}

	public boolean isPlaying() {
		return playing;
	}

	public void tick(Minecraft client) {
		if (!playing) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null) {
			stop();
			return;
		}

		List<Keyframe> frames = sequence.keyframes();
		Keyframe last = frames.get(frames.size() - 1);

		if (frames.size() == 1 || last.time() <= 0f) {
			apply(player, last.x(), last.y(), last.z(), last.yaw(), last.pitch());
			stop();
			return;
		}
		if (elapsedTicks / 20.0f >= last.time()) {
			if (loop) {
				elapsedTicks = 0;
			} else {
				apply(player, last.x(), last.y(), last.z(), last.yaw(), last.pitch());
				stop();
				return;
			}
		}
		float time = elapsedTicks / 20.0f;

		int index = 0;
		while (index < frames.size() - 2 && frames.get(index + 1).time() <= time) {
			index++;
		}

		Keyframe p1 = frames.get(index);
		Keyframe p2 = frames.get(index + 1);
		Keyframe p0 = index > 0 ? frames.get(index - 1) : p1;
		Keyframe p3 = index + 2 < frames.size() ? frames.get(index + 2) : p2;

		float segmentDuration = p2.time() - p1.time();
		float t = segmentDuration <= 0f ? 0f : Mth.clamp((time - p1.time()) / segmentDuration, 0f, 1f);

		double x = catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), t);
		double y = catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), t);
		double z = catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), t);
		float yaw = lerpAngle(p1.yaw(), p2.yaw(), t);
		float pitch = Mth.lerp(t, p1.pitch(), p2.pitch());

		apply(player, x, y, z, yaw, pitch);
		elapsedTicks++;
	}

	private static void apply(LocalPlayer player, double x, double y, double z, float yaw, float pitch) {
		player.setPos(x, y, z);
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}

	private static double catmullRom(double p0, double p1, double p2, double p3, float t) {
		double t2 = (double) t * t;
		double t3 = t2 * t;
		return 0.5 * ((2 * p1)
				+ (-p0 + p2) * t
				+ (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
				+ (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	private static float lerpAngle(float from, float to, float t) {
		return from + Mth.wrapDegrees(to - from) * t;
	}
}
