package net.coderbot.iris.colorspace;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.GlStateManager;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ComputeProgram;
import net.coderbot.iris.gl.program.Program;
import net.coderbot.iris.gl.program.ProgramBuilder;
import net.coderbot.iris.gl.texture.InternalTextureFormat;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.postprocess.FullScreenQuadRenderer;
import net.coderbot.iris.shaderpack.StringPair;
import net.coderbot.iris.shaderpack.preprocessor.JcppProcessor;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ColorSpaceFragmentConverter implements ColorSpaceConverter {
	private int width;
	private int height;
	private ColorSpace colorSpace;
	private Program program;
	private GlFramebuffer framebuffer;
	private int swapTexture;

	private int target;
	public ColorSpaceFragmentConverter(int width, int height, ColorSpace colorSpace) {
		rebuildProgram(width, height, colorSpace);
	}

	public void rebuildProgram(int width, int height, ColorSpace colorSpace) {
		if (program != null) {
			program.destroy();
			program = null;
			framebuffer.destroy();
			framebuffer = null;
			GlStateManager._deleteTexture(swapTexture);
			swapTexture = 0;
		}

		this.width = width;
		this.height = height;
		this.colorSpace = colorSpace;

		String vertexSource;
		String source;
		try {
			vertexSource = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/colorSpace.vsh"))), StandardCharsets.UTF_8);
			source = new String(IOUtils.toByteArray(Objects.requireNonNull(getClass().getResourceAsStream("/colorSpace.csh"))), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<StringPair> defineList = new ArrayList<>();
		defineList.add(new StringPair("CURRENT_COLOR_SPACE", String.valueOf(colorSpace.ordinal())));

		for (ColorSpace space : ColorSpace.values()) {
			defineList.add(new StringPair(space.name(), String.valueOf(space.ordinal())));
		}
		source = JcppProcessor.glslPreprocessSource(source, defineList);

		ProgramBuilder builder = ProgramBuilder.begin("colorSpaceFragment", vertexSource, null, source, ImmutableSet.of());

		builder.uniformMatrix(UniformUpdateFrequency.ONCE, "projection", () -> new Matrix4f(2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, -1, -1, 0, 1));
		builder.addDynamicSampler(() -> target, "readImage");

		swapTexture = GlStateManager._genTexture();
		GL46C.glTextureStorage2D(swapTexture, 1, GL30C.GL_RGBA8, width, height);

		this.framebuffer = new GlFramebuffer();
		framebuffer.addColorAttachment(0, swapTexture);
		this.program = builder.build();
	}

	public void process(int targetImage) {
		if (colorSpace == ColorSpace.SRGB) return;

		this.target = targetImage;
		program.use();
		framebuffer.bind();
		FullScreenQuadRenderer.INSTANCE.render();
		Program.unbind();
		GL46C.glCopyImageSubData(this.swapTexture, GL43C.GL_TEXTURE_2D, 0, 0, 0, 0, targetImage, GL43C.GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
	}
}
