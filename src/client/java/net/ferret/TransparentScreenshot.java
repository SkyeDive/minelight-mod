package net.ferret;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.Action;

// Class "overriding" methods of Screenshot to save with transparency
public class TransparentScreenshot extends Screenshot {
	
	public TransparentScreenshot(File file, int i, int j, int k) throws IOException {
		super(file, i, j, k);
	}
	
	// the following methods are copied from vanilla
	// static methods cannot be overridden, but we still want this class to be associated with Screenshot
	// and a mixin would be too intrusive and indiscriminate
	
	public static void grab(File file, RenderTarget renderTarget, Consumer<Component> consumer) {
		grab(file, null, renderTarget, consumer);
	}
	
	public static void grab(File file, @Nullable String string, RenderTarget renderTarget, Consumer<Component> consumer) {
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.recordRenderCall(() -> _grab(file, string, renderTarget, consumer));
		} else {
			_grab(file, string, renderTarget, consumer);
		}
	}
	
	private static void _grab(File file, @Nullable String string, RenderTarget renderTarget, Consumer<Component> consumer) {
		NativeImage nativeImage = takeScreenshot(renderTarget);
		File file2 = new File(file, "screenshots");
		file2.mkdir();
		File file3;
		if (string == null) {
			file3 = getFile(file2);
		} else {
			file3 = new File(file2, string);
		}

		Util.ioPool()
			.execute(
				() -> {
					try {
						nativeImage.writeToFile(file3);
						Component component = Component.literal(file3.getName())
							.withStyle(ChatFormatting.UNDERLINE)
							.withStyle(style -> style.withClickEvent(new ClickEvent(Action.OPEN_FILE, file3.getAbsolutePath())));
						consumer.accept(Component.translatable("screenshot.success", new Object[]{component}));
					} catch (Exception var7) {
						BodySharing.LOGGER.warn("Couldn't save screenshot", (Throwable)var7);
						consumer.accept(Component.translatable("screenshot.failure", new Object[]{var7.getMessage()}));
					} finally {
						nativeImage.close();
					}
				}
			);
	}
	
	public static NativeImage takeScreenshot(RenderTarget renderTarget) {
		int i = renderTarget.width;
		int j = renderTarget.height;
		NativeImage nativeImage = new NativeImage(i, j, false);
		RenderSystem.bindTexture(renderTarget.getColorTextureId());
		// pass in false to not replace alpha values
		nativeImage.downloadTexture(0, false);
		nativeImage.flipY();
		return nativeImage;
	}
	
	private static File getFile(File file) {
		String string = Util.getFilenameFormattedDateTime();
		int i = 1;
		
		while (true) {
			// adding guard condition to avoid undefined hang
			if (i > 100)
				throw new RuntimeException("No space for screenshot!");
			File file2 = new File(file, string + (i == 1 ? "" : "_" + i) + ".png");
			if (!file2.exists()) {
				return file2;
			}

			i++;
		}
	}
}
