package me.wasteofti;

import me.wasteofti.Commands.BasefinderSpiral;
import me.wasteofti.Modules.BasefinderModule;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.plugin.Plugin;

/**
 * Example rusherhack plugin
 *
 * @author John200410
 * @author p529
 */
public class Basefinder extends Plugin {
	
	@Override
	public void onLoad() {
		
		//logger
		this.getLogger().info("All your bases are belong to us");

		final BasefinderModule basefinderModule = new BasefinderModule();
		RusherHackAPI.getModuleManager().registerFeature(basefinderModule);
		
		//creating and registering a new command
		final BasefinderSpiral basefinderSpiralCommand = new BasefinderSpiral();
		RusherHackAPI.getCommandManager().registerFeature(basefinderSpiralCommand);
	}
	
	@Override
	public void onUnload() {
		this.getLogger().info("Basefinder plugin unloaded!");
	}
	
}