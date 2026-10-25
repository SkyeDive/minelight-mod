package net.ferret;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;

import com.google.common.graph.ElementOrder.Type;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.BufferBuilder.RenderedBuffer;
import com.mojang.brigadier.arguments.BoolArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.ferret.mixin.client.KeyboardHandlerAccess;
import net.ferret.mixin.client.MouseHandlerAccess;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;

public class BodySharingClient implements ClientModInitializer {
	public static HashMap<Integer, Boolean> possessorKeys = new HashMap<>();
	public static HashMap<Integer, Boolean> selfKeyRecord = new HashMap<>();
	public static ArrayList<Integer> selfUpdatedKeys = new ArrayList<>();
	public static boolean isPossessing = false;
	public static @Nullable UUID possessing = null;
	public static boolean isHosting = false;
	public static int streamWidth = 848;
	public static int streamHeight = 480;
	public static BufferedImage renderBMP = new BufferedImage(streamWidth, streamHeight, BufferedImage.TYPE_3BYTE_BGR);
	public static Graphics rbGraphics = renderBMP.createGraphics();
	public static KeyMapping stopPossession = new KeyMapping("key.minelight.stop_possess", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), KeyMapping.CATEGORY_MULTIPLAYER);
	public static KeyMapping getGUI = new KeyMapping("key.minelight.get_gui", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), KeyMapping.CATEGORY_INVENTORY);
	public static ArrayList<KeyMapping> guiKeys = new ArrayList<>();
	public static TextureTarget guiTarget;
	public static RenderBuffers renderBuffers = new RenderBuffers();
	public static @Nullable NativeImage guiFrameLast = null;
	public static @Nullable NativeImage guiFrame = null;
	
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		
		Minecraft.getInstance().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			guiTarget = new TextureTarget(
				mc.getMainRenderTarget().width,
				mc.getMainRenderTarget().height,
				true, Minecraft.ON_OSX);
		});
		
		// Possessor copycat GUI
		MenuScreens.register(BodySharing.VESSEL_SCREEN_MENU, VesselScreen::new);
		
		// Keybinds
		KeyBindingHelper.registerKeyBinding(stopPossession);
		guiKeys.add(KeyBindingHelper.registerKeyBinding(getGUI));
		
		// Debug clientside command for field manipulation
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("mlc")
				.then(ClientCommandManager.literal("host")
					.then(ClientCommandManager.argument("isHosting", BoolArgumentType.bool())
					.executes(context -> {
						Boolean newHostingVal = context.getArgument("isHosting", Boolean.class);
						isHosting = newHostingVal;
						context.getSource().sendFeedback(Component.literal("Set isHosting to " + String.valueOf(newHostingVal)));
						return 0;
					}))
				)
			);
		});
		
		// run every tick, clientside
		ClientTickEvents.START_CLIENT_TICK.register((minecraftClient) -> {
			// Debug hotkey for testing GUI code
			if (getGUI.consumeClick()) {
				RenderSystem.recordRenderCall(() -> {
					renderGUI();
					// TODO figure out how to extract the buffer data from guiGraphics to append into the spirit's screen
					BodySharing.LOGGER.info("Rendered GUI into buffer");
				});
			}
			
			if (minecraftClient.isRunning() && minecraftClient.getCurrentServer() != null) {
				// Hotkey to stop possessing
				if (stopPossession.consumeClick()) {
					if (isPossessing) {
						requestStopPossessing();
					}
				}
					
				// Vessel per-tick code
				if (isHosting) {
					// Old code for screen-capturing using screenshotting, unused but kept as a PoC if it's needed again
					
					/*
					// First timing checkpoint
					long c0 = System.currentTimeMillis();
					// Screenshot into nonstandard format (AABBGGRR)
					Minecraft clientGame = Minecraft.getInstance();
					NativeImage renderImage = Screenshot.takeScreenshot(clientGame.getMainRenderTarget());
					int[] renderPixelsRGBA = renderImage.getPixelsRGBA();
					// instead of RrGgBbAa, stored as AaBbGgRr
					BodySharing.LOGGER.info("Formatting screenshot");
					isHosting = false;
					
					int[] nnPixelsRGBA = new int[streamWidth * streamHeight];
					for (int pixelIndex = 0; pixelIndex < nnPixelsRGBA.length; pixelIndex++) {
						// TODO copy nearest-neighbor style from renderPixelsRGBA into nnPixelsRGBA
					}
					long c1 = System.currentTimeMillis();
					
					// Arrange nonstandard image data into convertible BMP format
					try {
					long c2 = System.currentTimeMillis();
						byte[] bmpBytes = screenshotToBitmap(nnPixelsRGBA, renderImage.getWidth());
						if (bmpBytes == null)
							throw new IOException("Screenshot was null!");
						long c3 = System.currentTimeMillis();
						BufferedImage bufferedBMP = ImageIO.read(new ByteArrayInputStream(bmpBytes));
						long c4 = System.currentTimeMillis();
						rbGraphics.drawImage(bufferedBMP, 0, 0, streamWidth, streamHeight, null);
						long c5 = System.currentTimeMillis();
						// Set up JPEG converter
						ImageWriter jpegWriter = ImageIO.getImageWritersByFormatName("jpg").next();
						ImageWriteParam jpegWriterParams = jpegWriter.getDefaultWriteParam();
						// Set JPEG encoding parameters
						jpegWriterParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						jpegWriterParams.setCompressionQuality(0.9f);
						// Create JPEG holder
						ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
						MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(jpegBytes);
						jpegWriter.setOutput(mcios);
						// Convert
						jpegWriter.write(new IIOImage(renderBMP, null, null));
						jpegWriter.dispose();
						mcios.close();
						long c6 = System.currentTimeMillis();
						
						BodySharing.LOGGER.info("Converted screenshot");
						
						// Save converted screenshot
						//FileOutputStream saveSpot = new FileOutputStream("/tmp/sc.jpg");
						//saveSpot.write(jpegBytes.toByteArray());
						//saveSpot.close();
						
						// Print timing stats to chat
						clientGame.execute(() -> clientGame.gui.getChat().addMessage(Component.literal(String.format(
							"Timing stats: sc %d nn %d mkbmp %d parse %d resize %d jpeg %d",
							(int)(c1-c0), (int)(c2-c1), (int)(c3-c2), (int)(c4-c3), (int)(c5-c4), (int)(c6-c5)
						))));
						// TODO client sends packet VESSEL_SCREEN
						
					} catch (IOException e) {
						BodySharing.LOGGER.error(e.toString());
					}
					*/
					
				// Possessor per-tick code
				} else if (isPossessing) {
					FriendlyByteBuf packet = PacketByteBufs.create();
					// Encode held keys
					// Stored in string as <int><boolean>;<int><boolean>
					String keyUpdateCode = "";
					for (Integer keysym : selfUpdatedKeys) {
						keyUpdateCode += keysym.toString();
						keyUpdateCode += selfKeyRecord.getOrDefault(keysym, false) ? "T" : "F";
						keyUpdateCode += ";";
					}
					// Ensure no trailing semicolon
					if (keyUpdateCode.length() > 0)
						keyUpdateCode = keyUpdateCode.substring(0, keyUpdateCode.length() - 1);
					// Store data
					packet.writeInt(keyUpdateCode.length());
					packet.writeCharSequence(keyUpdateCode, StandardCharsets.US_ASCII);
					selfUpdatedKeys.clear();
					// Bounce over server to vessels
					// client sends packet UPDATE_KEYS
					ClientPlayNetworking.send(BodySharing.UPDATE_KEYS_ID, packet);
				}
			}
		});
		
		// client receives packet UPDATE_KEYS
		// update state of held keys, as influenced by this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.UPDATE_KEYS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			int codeLength = buffer.readInt();
			CharSequence keyUpdateSequence = buffer.readCharSequence(codeLength, StandardCharsets.US_ASCII);
			String keyUpdateCode = String.valueOf(keyUpdateSequence);
			if (keyUpdateCode.length() > 0)
				for (String update : keyUpdateCode.split(";")) {
					if (update.length() != 0) {
						int keysym = Integer.valueOf(update.substring(0, update.length() - 1));
						boolean value = update.charAt(update.length() - 1) == 'T' ? true : false;
						possessorKeys.put(keysym, value);
					}
				}
		});
		
		// client receives packet KEY_PRESS
		// Receive and process keypress events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.KEY_PRESS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int keysym = buffer.readInt();
			int scancode = buffer.readInt();
			int action = buffer.readInt();
			int mods = buffer.readInt();
			minecraftClient.execute(() -> {
				if (!(minecraftClient.screen == null && keysym == InputConstants.KEY_ESCAPE))
					minecraftClient.keyboardHandler.keyPress(window, keysym, scancode, action, mods);
			});
		});

		// client receives packet CHAR_TYPE
		// Receive and process chartype events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.CHAR_TYPE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int codepoint = buffer.readInt();
			int mods = buffer.readInt();
			minecraftClient.execute(() ->
				((KeyboardHandlerAccess)(minecraftClient.keyboardHandler)).bodySharingCharTyped(window, codepoint, mods));
		});

		// client receives packet MOUSE_MOVE
		// Receive and process mouse movement events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_MOVE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			double d = buffer.readDouble();
			double e = buffer.readDouble();
			((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnMove(window, d, e);
		});

		// client receives packet MOUSE_PRESS
		// Receive and process mouse click events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_PRESS_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			int i = buffer.readInt();
			int j = buffer.readInt();
			int k = buffer.readInt();
			minecraftClient.execute(() ->
				((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnPress(window, i, j, k));
		});

		// client receives packet MOUSE_SCROLL
		// Receive and process mouse scrolling events from this client's possessors
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.MOUSE_SCROLL_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			long window = minecraftClient.getWindow().getWindow();
			double d = buffer.readDouble();
			double e = buffer.readDouble();
			minecraftClient.execute(() ->
				((MouseHandlerAccess)(minecraftClient.mouseHandler)).bodySharingOnScroll(window, d, e));
		});
		
		// client receives packet POSSESSING_UPDATE
		// update this client's state of isPossessing
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.POSSESSING_UPDATE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			boolean end = buffer.readBoolean();
			UUID vessel = buffer.readUUID();
			if (!end) {
				if (!isPossessing)
					BodySharing.LOGGER.info("Beginning possessing");
				possessing = vessel;
			} else {
				if (isPossessing)
					BodySharing.LOGGER.info("Stopping possession");
				possessing = null;
				selfUpdatedKeys.clear();
			}
			isPossessing = !end;
		});
		
		// client receives packet VESSEL_UPDATE
		// update this client's state of isHosting
		ClientPlayNetworking.registerGlobalReceiver(BodySharing.VESSEL_UPDATE_ID, (minecraftClient, clientPacketListener, buffer, packetSender) -> {
			boolean isVessel = buffer.readBoolean();
			if (!isVessel) {
				if (isHosting)
					BodySharing.LOGGER.info("Stopping hosting");
				possessorKeys.clear();
			} else {
				if (!isHosting)
					BodySharing.LOGGER.info("Beginning hosting");
			}
			isHosting = isVessel;
		});
	}
	
	// Send packet to server to request to stop being a possessor
	public static void requestStopPossessing() {
		// Immediately update locally--the server will probably approve the request
		isPossessing = false;
		
		// Notify server
		FriendlyByteBuf startPossessPacket = PacketByteBufs.create();
		startPossessPacket.writeBoolean(true);
		Minecraft mc = Minecraft.getInstance();
		startPossessPacket.writeUUID(mc.player.getUUID());
		possessing = null;
		// client sends packet POSSESSING_UPDATE
		ClientPlayNetworking.send(BodySharing.POSSESSING_UPDATE_ID, startPossessPacket);
	}
	
	// Try to re-render the currently-opened GUI; update client fields on success
	public static boolean renderGUI() {
		Minecraft minecraftClient = Minecraft.getInstance();
		if (minecraftClient.screen == null)
			return false;
		
		// change render target
		// always resize rendering target to ensure correct size
		Window window = minecraftClient.getWindow();
		guiTarget.resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
		// rebind targets
		RenderTarget mrt = minecraftClient.getMainRenderTarget();
		mrt.unbindWrite();
		guiTarget.bindWrite(false);
		
		// set up pose et al for screen translation
		Matrix4f matrix4f = new Matrix4f()
			.setOrtho(0.0F, (float)(window.getWidth() / window.getGuiScale()), (float)(window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F);
		RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
		PoseStack poseStack = RenderSystem.getModelViewStack();
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(0.0F, 0.0F, -11000.0F);
		RenderSystem.applyModelViewMatrix();
		Lighting.setupFor3DItems();
		
		// render
		int guiMX = (int)(minecraftClient.mouseHandler.xpos() * minecraftClient.getWindow().getGuiScaledWidth() / minecraftClient.getWindow().getScreenWidth());
		int guiMY = (int)(minecraftClient.mouseHandler.ypos() * minecraftClient.getWindow().getGuiScaledHeight() / minecraftClient.getWindow().getScreenHeight());
		GuiGraphics guiGraphics = new GuiGraphics(minecraftClient, renderBuffers.bufferSource());
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
		RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		minecraftClient.screen.renderWithTooltip(guiGraphics, guiMX, guiMY, minecraftClient.getDeltaFrameTime());
		
		// cleanup
		guiGraphics.flush();
		poseStack.popPose();
		RenderSystem.applyModelViewMatrix();
		guiTarget.unbindWrite();
		mrt.bindWrite(Minecraft.ON_OSX);
		// screenshot
		NativeImage screenshot = TransparentScreenshot.takeScreenshot(guiTarget);
		BodySharingClient.guiFrameLast = BodySharingClient.guiFrame;
		BodySharingClient.guiFrame = screenshot;
		return true;
	}
	
	public static @Nullable NativeImage renderHUD() {
		Minecraft minecraftClient = Minecraft.getInstance();
		if (minecraftClient.gui == null)
			return null;
		
		// change render target
		// always resize rendering target to ensure correct size
		Window window = minecraftClient.getWindow();
		guiTarget.resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
		// rebind targets
		RenderTarget mrt = minecraftClient.getMainRenderTarget();
		mrt.unbindWrite();
		guiTarget.bindWrite(false);
		
		// set up pose et al for screen translation
		Matrix4f matrix4f = new Matrix4f()
			.setOrtho(0.0F, (float)(window.getWidth() / window.getGuiScale()), (float)(window.getHeight() / window.getGuiScale()), 0.0F, 1000.0F, 21000.0F);
		RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);
		PoseStack poseStack = RenderSystem.getModelViewStack();
		poseStack.pushPose();
		poseStack.setIdentity();
		poseStack.translate(0.0F, 0.0F, -11000.0F);
		RenderSystem.applyModelViewMatrix();
		Lighting.setupFor3DItems();

		// render
		GuiGraphics guiGraphics = new GuiGraphics(minecraftClient, renderBuffers.bufferSource());
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 0.0f);
		RenderSystem.clear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		// doesn't keep transparency--probably because of a RenderSystem call in here
		minecraftClient.gui.render(guiGraphics, minecraftClient.getFrameTime());
		
		// cleanup
		guiGraphics.flush();
		poseStack.popPose();
		RenderSystem.applyModelViewMatrix();
		guiTarget.unbindWrite();
		mrt.bindWrite(Minecraft.ON_OSX);
		// screenshot
		NativeImage screenshot = TransparentScreenshot.takeScreenshot(guiTarget);
		return screenshot;
	}
	
	// Old code for image format conversion--unused but kept as a PoC in case it's needed again
	
	/*
	// Convert to big-endian
	public static byte[] keep_3b(int num) {
		byte[] out = new byte[3];
		out[0] = (byte)((num >> 16) & 0xFF);
		out[1] = (byte)((num >> 8) & 0xFF);
		out[2] = (byte)(num & 0xFF);
		return out;
	}
	// Convert to little-endian
	public static byte[] inv_4b(int num) {
		byte[] out = new byte[4];
		out[0] = (byte)(num & 0xFF);
		out[1] = (byte)((num >> 8) & 0xFF);
		out[2] = (byte)((num >> 16) & 0xFF);
		out[3] = (byte)((num >> 24) & 0xFF);
		return out;
	}
	
	// Convert Minecraft's screenshot format into Windows BMP
	public static byte[] screenshotToBitmap(int[] screen, int width) {
		final int bpc = 8;
		final int channels = 3;
		final int bpp = bpc * channels;
		final int height = screen.length / width;
		// Cancel if screenshot isn't rectangular
		if ((float)height != (float)screen.length / width)
			return null;
		// en.wikipedia.org/wiki/BMP_file_format#Pixel_storage
		final int rowSize = net.minecraft.util.Mth.ceil((float)bpp * width / 32) * 4;
		final int rowEndPad = rowSize - channels * width;
		final int pixelArraySize = rowSize * height;
		
		// Start at byte 54--14 byte BMP header + 40 byte DIB header
		final int bmpHeaderSize = 14;
		final int dibHeaderSize = 40;
		final int pixelArrayStart = bmpHeaderSize + dibHeaderSize;
		final int sizeInBytes = pixelArrayStart + pixelArraySize;
		ByteArrayOutputStream bmp = new ByteArrayOutputStream(sizeInBytes);
		
		// Set up BMP header
		bmp.write('B'); bmp.write('M'); // literally the only non-OS/2 option
		bmp.writeBytes(inv_4b(sizeInBytes)); // self explanatory
		bmp.write(0); bmp.write(0); // pointless field
		bmp.write(0); bmp.write(0); // pointless field
		bmp.writeBytes(inv_4b(pixelArrayStart)); // technically always 0x36 00 00 00
		
		// Set up DIB header - Windows BITMAPINFOHEADER
		bmp.writeBytes(inv_4b(dibHeaderSize)); // technically always 0x28 00 00 00
		bmp.writeBytes(inv_4b(width));
		bmp.writeBytes(inv_4b(-height)); // negative = top-to-bottom
		bmp.write(1); bmp.write(0); // "must be 1"
		bmp.write(bpp); bmp.write(0);
		bmp.writeBytes(inv_4b(0)); // BI_RGB
		bmp.writeBytes(inv_4b(0)); // this can be 0 because we used BI_RGB
		bmp.writeBytes(inv_4b(1)); // this stretch doesn't matter
		bmp.writeBytes(inv_4b(1));
		bmp.writeBytes(inv_4b(0));
		bmp.writeBytes(inv_4b(0));
		
		// Translate format
		
		for (int y = 0; y < height; y++) {
			// Write a row
			for (int x = 0; x < width; x++) {
				// This data is AABBGGRR, so it's 1-to-1 with the 24bpp (bi_rgb) wbmp format
				bmp.writeBytes(keep_3b(screen[y * width + x]));
			}
			// Pad with bytes afterwards if needed
			for (int i = 0; i < rowEndPad; i++)
				bmp.write(0);
		}
		
		return bmp.toByteArray();
	}
	*/
}