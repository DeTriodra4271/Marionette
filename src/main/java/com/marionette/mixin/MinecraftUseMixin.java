package com.marionette.mixin;

import com.marionette.recording.Recorder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla reaches {@code startUseItem} two ways: once per queued click, and
 * again every few ticks while the use key is merely held (bridging by
 * holding right-click). Only the first bumps the key's click counter, so
 * counting calls here is the only way to record both.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"))
	private void marionette$countUse(CallbackInfo ci) {
		Recorder.noteUseAttempt();
	}
}
