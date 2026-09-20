package com.marionette.keyframe;

/**
 * One posed point on a cinematic path: a position and look direction, and
 * the moment (in seconds from the start of the take) it should be reached.
 */
public record Keyframe(double x, double y, double z, float yaw, float pitch, float time) {
}
