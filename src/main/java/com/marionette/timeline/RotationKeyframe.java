package com.marionette.timeline;

/**
 * A look-direction keyframe: at {@code time} seconds, face {@code yaw}/
 * {@code pitch}. Playback interpolates between consecutive keyframes.
 */
public record RotationKeyframe(float time, float yaw, float pitch) {
}
