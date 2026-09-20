package com.marionette.keyframe;

import java.util.List;

/**
 * A named, time-ordered set of {@link Keyframe}s — a Blender-style
 * animation path, played back by smoothly interpolating between poses
 * rather than replaying raw per-tick input.
 */
public record KeyframeSequence(String name, List<Keyframe> keyframes) {
}
