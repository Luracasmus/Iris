package net.irisshaders.iris.mixin.fabric;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.NeoLambdas;
import net.irisshaders.iris.fantastic.ParticleRenderingPhase;
import net.irisshaders.iris.fantastic.PhasedParticleEngine;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the PhasedParticleManager changes to render opaque particles much earlier than other particles.
 * <p>
 * See the comments in {@link MixinParticleEngine} for more details.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private RenderBuffers renderBuffers;

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void iris$resetParticleManagerPhase(GraphicsResourceAllocator graphicsResourceAllocator, DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
		((PhasedParticleEngine) minecraft.particleEngine).setParticleRenderingPhase(ParticleRenderingPhase.EVERYTHING);
	}

	@Inject(method = { "method_62214" }, require = 1, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"))
	private void iris$renderOpaqueParticles(FogParameters fogParameters, DeltaTracker deltaTracker, Camera camera, ProfilerFiller profilerFiller, Matrix4f matrix4f, Matrix4f matrix4f2, ResourceHandle resourceHandle, ResourceHandle resourceHandle2, ResourceHandle resourceHandle3, ResourceHandle resourceHandle4, boolean bl, Frustum frustum, ResourceHandle resourceHandle5, CallbackInfo ci) {
		Profiler.get().popPush("opaque_particles");

		ParticleRenderingSettings settings = getRenderingSettings();

		float f = deltaTracker.getGameTimeDeltaPartialTick(false);

		if (settings == ParticleRenderingSettings.BEFORE) {
			minecraft.particleEngine.render(camera, f, this.renderBuffers.bufferSource());
		} else if (settings == ParticleRenderingSettings.MIXED) {
			((PhasedParticleEngine) minecraft.particleEngine).setParticleRenderingPhase(ParticleRenderingPhase.OPAQUE);
			minecraft.particleEngine.render(camera, f, this.renderBuffers.bufferSource());
		}
	}

	@Redirect(method = "method_62213", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"))
	private void iris$renderTranslucentAfterDeferred(ParticleEngine instance, Camera camera, float f, MultiBufferSource.BufferSource bufferSource) {
		ParticleRenderingSettings settings = getRenderingSettings();

		if (settings == ParticleRenderingSettings.AFTER) {
			minecraft.particleEngine.render(camera, f, bufferSource);
		} else if (settings == ParticleRenderingSettings.MIXED) {
			((PhasedParticleEngine) minecraft.particleEngine).setParticleRenderingPhase(ParticleRenderingPhase.TRANSLUCENT);
			minecraft.particleEngine.render(camera, f, bufferSource);
		}
	}

	private ParticleRenderingSettings getRenderingSettings() {
		return Iris.getPipelineManager().getPipeline().map(WorldRenderingPipeline::getParticleRenderingSettings).orElse(ParticleRenderingSettings.MIXED);
	}
}
