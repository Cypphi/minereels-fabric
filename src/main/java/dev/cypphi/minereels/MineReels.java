package dev.cypphi.minereels;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MineReels implements ModInitializer {
	public static final String MOD_ID = "minereels";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("MineReels initialized");
	}
}
