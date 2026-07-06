package dev.cypphi.minereels.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cypphi.minereels.MineReels;
import dev.cypphi.minereels.config.OverlayConfig;
import dev.cypphi.minereels.input.MultiTapDetector;
import dev.cypphi.minereels.reel.InstagramProvider;
import dev.cypphi.minereels.reel.ReelProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MineReelsClient implements ClientModInitializer {
	private static final long TAP_WINDOW_MILLIS = 350;

	private final KeyMapping.Category category =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MineReels.MOD_ID, "controls"));

	@Override
	public void onInitializeClient() {
		ReelProvider provider = createProvider();
		ReelFeedOverlay overlay = new ReelFeedOverlay(provider);

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MineReels.MOD_ID, "reel_feed"),
				overlay::render
		);
		overlay.registerChatInteraction();
		registerKeys(overlay);
		MineReels.LOGGER.info("MineReels client initialized (provider: {})", provider.name());
	}

	private static ReelProvider createProvider() {
		String cookie = OverlayConfig.get().instagramCookie;
		if (cookie == null || cookie.isBlank()) {
			MineReels.LOGGER.warn("No Instagram cookie set — the overlay will be empty. Add it in the MineReels config (Tokens tab).");
		}
		return new InstagramProvider(cookie == null ? "" : cookie);
	}

	private void registerKeys(ReelFeedOverlay overlay) {
		KeyMapping next = key("next-reel", GLFW.GLFW_KEY_DOWN);
		KeyMapping previous = key("previous-reel", GLFW.GLFW_KEY_UP);
		KeyMapping like = key("like-reel", GLFW.GLFW_KEY_L);
		KeyMapping toggle = key("toggle-overlay", GLFW.GLFW_KEY_INSERT);
		// Multi-tap control: 1 tap = next, 2 = like, 3 = previous.
		KeyMapping action = key("reel-action", GLFW.GLFW_KEY_RIGHT);
		MultiTapDetector tapDetector = new MultiTapDetector(TAP_WINDOW_MILLIS);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (next.consumeClick()) {
				overlay.nextReel();
			}
			while (previous.consumeClick()) {
				overlay.previousReel();
			}
			while (like.consumeClick()) {
				overlay.likeCurrent();
			}
			while (toggle.consumeClick()) {
				overlay.toggleEnabled();
			}
			while (action.consumeClick()) {
				tapDetector.onTap();
			}
			dispatchMultiTap(tapDetector.poll(), overlay);
		});
	}

	private static void dispatchMultiTap(int taps, ReelFeedOverlay overlay) {
		switch (taps) {
			case 1 -> overlay.nextReel();
			case 2 -> overlay.likeCurrent();
			case 3 -> overlay.previousReel();
			default -> {
			}
		}
	}

	private KeyMapping key(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + MineReels.MOD_ID + "." + name, InputConstants.Type.KEYSYM, defaultKey, category));
	}
}
