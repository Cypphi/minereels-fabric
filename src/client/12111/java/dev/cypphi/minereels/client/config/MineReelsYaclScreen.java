package dev.cypphi.minereels.client.config;

import dev.cypphi.minereels.config.OverlayConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Builds the ModMenu config screen: General, Performance, and Tokens tabs. */
public final class MineReelsYaclScreen {
	private MineReelsYaclScreen() {
	}

	public static Screen create(Screen parent) {
		OverlayConfig config = OverlayConfig.get();

		ConfigCategory general = ConfigCategory.createBuilder()
				.name(Text.literal("General"))
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Enabled"))
						.description(OptionDescription.of(Text.literal("Master toggle for the reel overlay.")))
						.binding(true, () -> config.enabled, v -> config.enabled = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Show in HUD"))
						.description(OptionDescription.of(Text.literal("Show the overlay on the HUD. Right-clicking the card while chat is open also toggles this.")))
						.binding(true, () -> config.showInHud, v -> config.showInHud = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Card size (%)"))
						.description(OptionDescription.of(Text.literal("Card width as a percentage of the screen. Text scales with the card.")))
						.binding(18.0, () -> config.scalePercent, v -> config.scalePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(5.0, 60.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Volume (%)"))
						.description(OptionDescription.of(Text.literal("Playback volume for reel audio.")))
						.binding(100.0, () -> config.volumePercent, v -> config.volumePercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("X position (%)"))
						.binding(2.0, () -> config.xPercent, v -> config.xPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Y position (%)"))
						.binding(5.0, () -> config.yPercent, v -> config.yPercent = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 100.0).step(0.5))
						.build())
				.build();

		ConfigCategory performance = ConfigCategory.createBuilder()
				.name(Text.literal("Performance"))
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Play videos"))
						.description(OptionDescription.of(Text.literal("When disabled, reels render thumbnails only and no video FFmpeg process is started.")))
						.binding(true, () -> config.playVideos, v -> config.playVideos = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Text.literal("Play audio"))
						.description(OptionDescription.of(Text.literal("When disabled, the separate audio FFmpeg process is not started.")))
						.binding(true, () -> config.playAudio, v -> config.playAudio = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Max video FPS"))
						.description(OptionDescription.of(Text.literal("Caps both FFmpeg decoded frames and render-thread texture uploads.")))
						.binding(24.0, () -> config.maxVideoFps, v -> config.maxVideoFps = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(5.0, 60.0).step(1.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Video height (px)"))
						.description(OptionDescription.of(Text.literal("Output height for decoded video textures. Lower values reduce CPU copy and GPU upload cost.")))
						.binding(720.0, () -> config.videoHeightPixels, v -> config.videoHeightPixels = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(240.0, 1080.0).step(60.0))
						.build())
				.option(Option.<Double>createBuilder()
						.name(Text.literal("Scroll animation (ms)"))
						.description(OptionDescription.of(Text.literal("Duration of the reel-to-reel vertical scroll animation. Set to 0 to disable.")))
						.binding(150.0, () -> config.scrollAnimationMillis, v -> config.scrollAnimationMillis = v)
						.controller(opt -> DoubleSliderControllerBuilder.create(opt).range(0.0, 500.0).step(25.0))
						.build())
				.build();

		ConfigCategory tokens = ConfigCategory.createBuilder()
				.name(Text.literal("Tokens"))
				.option(Option.<String>createBuilder()
						.name(Text.literal("Instagram cookie"))
						.description(OptionDescription.of(Text.literal(
								"The full Cookie header from a logged-in instagram.com session "
										+ "(contains sessionid + csrftoken). Leave blank to use the mock feed. "
										+ "Stored locally only — never share or commit this.")))
						.binding("", () -> config.instagramCookie, v -> config.instagramCookie = v)
						.controller(StringControllerBuilder::create)
						.build())
				.build();

		return YetAnotherConfigLib.createBuilder()
				.title(Text.literal("MineReels"))
				.category(general)
				.category(performance)
				.category(tokens)
				.save(OverlayConfig::save)
				.build()
				.generateScreen(parent);
	}
}
