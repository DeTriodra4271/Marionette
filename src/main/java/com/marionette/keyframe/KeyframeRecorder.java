package com.marionette.keyframe;

import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures explicit keyframes rather than sampling every tick: fly to a
 * pose, press "add keyframe," move on. The gap between two keyframes is
 * however long you actually waited between presses, so pacing a shot is
 * just a matter of taking your time (or not) between them.
 */
public class KeyframeRecorder {
	private final List<Keyframe> keyframes = new ArrayList<>();
	private boolean recording;
	private int elapsedTicks;

	public void start() {
		keyframes.clear();
		recording = true;
		elapsedTicks = 0;
	}

	public boolean isRecording() {
		return recording;
	}

	public int keyframeCount() {
		return keyframes.size();
	}

	public void tick() {
		if (recording) {
			elapsedTicks++;
		}
	}

	public int addKeyframe(LocalPlayer player) {
		if (!recording) {
			start();
		}
		keyframes.add(new Keyframe(
				player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot(),
				elapsedTicks / 20.0f
		));
		return keyframes.size();
	}

	public KeyframeSequence finish(String name) {
		recording = false;
		return new KeyframeSequence(name, new ArrayList<>(keyframes));
	}
}
