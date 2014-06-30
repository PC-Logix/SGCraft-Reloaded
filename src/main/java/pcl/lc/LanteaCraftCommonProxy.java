package pcl.lc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.event.terraingen.InitMapGenEvent;

import org.apache.logging.log4j.Level;

import pcl.common.helpers.AnalyticsHelper;
import pcl.common.helpers.ConfigValue;
import pcl.common.helpers.ConfigurationHelper;
import pcl.common.helpers.GUIHandler;
import pcl.common.helpers.NetworkHelpers;
import pcl.common.helpers.RegistrationHelper;
import pcl.common.helpers.VersionHelper;
import pcl.lc.base.TileEntityChunkManager;
import pcl.lc.base.network.ClientPacketHandler;
import pcl.lc.base.network.ModPacket;
import pcl.lc.base.network.PacketLogger;
import pcl.lc.base.network.ServerPacketHandler;
import pcl.lc.base.worldgen.NaquadahOreWorldGen;
import pcl.lc.client.audio.AudioEngine;
import pcl.lc.core.ModuleManager;
import pcl.lc.core.ModuleManager.Module;
import pcl.lc.core.RemoteChunkLoading;
import pcl.lc.core.ServerTickHandler;
import pcl.lc.core.WorldLog;
import pcl.lc.module.ModuleWorldGenerator;
import pcl.lc.module.compat.UpgradeHelper;
import pcl.lc.module.stargate.StargateConnectionManager;
import pcl.lc.module.stargate.tile.TileEntityStargateBase;
import pcl.lc.module.stargate.tile.TileEntityStargateController;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

public class LanteaCraftCommonProxy {

	/**
	 * @deprecated Pending deletion (see XML Configuration)
	 */
	@Deprecated
	protected ConfigurationHelper config;

	/**
	 * @deprecated Pending deletion (see XML Configuration)
	 */
	@Deprecated
	protected ArrayList<ConfigValue<?>> configValues = new ArrayList<ConfigValue<?>>();

	protected Map<String, ResourceLocation> resourceCache = new HashMap<String, ResourceLocation>();

	protected AnalyticsHelper analyticsHelper = new AnalyticsHelper(false, null);
	private VersionHelper versionHelper = new VersionHelper();

	protected ClientPacketHandler clientPacketHandler;
	protected PacketLogger clientPacketLogger;
	protected ServerPacketHandler serverPacketHandler;
	protected PacketLogger serverPacketLogger;

	private NetworkHelpers networkHelpers;
	private UpgradeHelper upgradeHelper;

	public TileEntityChunkManager chunkManager;
	protected RemoteChunkLoading remoteChunkManager;
	protected StargateConnectionManager stargateConnectionManager;
	protected AudioEngine audioContext;

	protected WorldLog worldLogger;

	private ServerTickHandler serverTickHandler = new ServerTickHandler(versionHelper);

	protected Map<Integer, Class<? extends Container>> registeredContainers = new HashMap<Integer, Class<? extends Container>>();
	protected Map<Integer, Class<? extends GuiScreen>> registeredGUIs = new HashMap<Integer, Class<? extends GuiScreen>>();
	protected Map<String, VillagerMapping> registeredVillagers = new HashMap<String, VillagerMapping>();
	public int tokraVillagerID;

	protected class VillagerMapping {
		public final int villagerID;
		public final ResourceLocation villagerSkin;

		public VillagerMapping(int id, ResourceLocation skin) {
			villagerID = id;
			villagerSkin = skin;
		}
	};

	protected ModuleManager moduleManager;

	public LanteaCraftCommonProxy() {
		if (BuildInfo.NET_DEBUGGING)
			serverPacketLogger = new PacketLogger(new File("lc-network-server.dat"));
		serverPacketHandler = new ServerPacketHandler(serverPacketLogger);
		networkHelpers = new NetworkHelpers();
		moduleManager = new ModuleManager();
	}

	public void preInit(FMLPreInitializationEvent e) {
		if (BuildInfo.isDevelopmentEnvironment())
			LanteaCraft.getLogger().log(
					Level.WARN,
					"This doesn't appear to be an official build of LanteaCraft, or you are in a development context; "
							+ "please do not report bugs to us. We do not support external builds.");
		else
			LanteaCraft.getLogger().log(Level.INFO,
					"Hello there, I'm LanteaCraft " + BuildInfo.versionNumber + "-" + BuildInfo.getBuildNumber() + "!");
		config = new ConfigurationHelper(e.getSuggestedConfigurationFile());
		configure();
		moduleManager.preInit(e);
	}

	public void init(FMLInitializationEvent e) {
		LanteaCraft.getLogger().log(Level.INFO, "LanteaCraft setting up...");
		MinecraftForge.EVENT_BUS.register(LanteaCraft.getInstance());
		MinecraftForge.EVENT_BUS.register(LanteaCraft.getSpecialBucketHandler());

		chunkManager = new TileEntityChunkManager(LanteaCraft.getInstance());
		remoteChunkManager = new RemoteChunkLoading();
		stargateConnectionManager = new StargateConnectionManager();
		serverTickHandler.registerTickHost(remoteChunkManager);
		serverTickHandler.registerTickHost(stargateConnectionManager);
		audioContext = new AudioEngine();

		NetworkRegistry.INSTANCE.registerGuiHandler(LanteaCraft.getInstance(), new GUIHandler());
		FMLCommonHandler.instance().bus().register(serverTickHandler);
		networkHelpers.init();
		moduleManager.init(e);
	}

	public void postInit(FMLPostInitializationEvent e) {
		RegistrationHelper.flagLateRegistrationZone();
		if (config.extended)
			config.save();
		LanteaCraft.getLogger().log(Level.INFO, "LanteaCraft done setting up!");

		LanteaCraft.getLogger().log(Level.INFO, "[COMPAT] LanteaCraft looking for other versions of SGCraft...");
		if (UpgradeHelper.detectSGCraftInstall() || UpgradeHelper.detectSGCraftReloadedInstall()) {
			upgradeHelper = new UpgradeHelper();
			if (UpgradeHelper.detectSGCraftInstall())
				upgradeHelper.hookSGCraft();
			if (UpgradeHelper.detectSGCraftReloadedInstall())
				upgradeHelper.hookSGCraftReloaded();
		}
		LanteaCraft.getLogger().log(Level.INFO, "[COMPAT] LanteaCraft done looking for other versions.");
		moduleManager.postInit(e);
	}

	public NaquadahOreWorldGen getOreGenerator() {
		if (!Module.WORLDGEN.isLoaded())
			return null;
		return ((ModuleWorldGenerator) Module.WORLDGEN.moduleOf()).getNaquadahOreGenerator();
	}

	public VersionHelper getVersionHelper() {
		return versionHelper;
	}

	public AudioEngine getAudioEngine() {
		return audioContext;
	}

	public void onInitMapGen(InitMapGenEvent e) {
		LanteaCraft.getLogger().log(Level.DEBUG, "InitMapGenEvent fired");
	}

	void configure() {
		TileEntityStargateController.configure(config);
		NaquadahOreWorldGen.configure(config);
		TileEntityStargateBase.configure(config);
		configValues.add(new ConfigValue<Boolean>("addOresToExistingWorlds", config.getBoolean("options",
				"addOresToExistingWorlds", false)));

		String version = new StringBuilder().append(BuildInfo.versionNumber).append(" build ")
				.append(BuildInfo.getBuildNumber()).toString();
		Property prop = config.get("general", "currentVersion", 0);
		prop.comment = "Version cache - do not change this!";
		String previousVersion = prop.getString();
		prop.set(version);

		Property GenerateStruct = config.get("options", "GenerateStructures", true);
		configValues.add(new ConfigValue<Boolean>("doGenerateStructures", config.getBoolean("stargate",
				"GenerateStructures", true)));
		GenerateStruct.comment = "Enables/Disables generation of Gate Rooms under Desert Pyramids. (true/false)";

		Property textureRes = config.get("graphics_options", "textureRes", 32);
		configValues.add(new ConfigValue<Integer>("renderQuality", config.getInteger("graphics_options", "textureRes",
				32)));
		textureRes.comment = "Texture resolution setting. (16 / 32 / 64 / 128)";

		Property HDModels = config.get("graphics_options", "HDModels", true);
		configValues.add(new ConfigValue<Boolean>("renderUseModels", config.getBoolean("graphics_options", "HDModels",
				true)));
		HDModels.comment = "Should HD models be used. (true/false)";

		configValues.add(new ConfigValue<Boolean>("doGateExplosion", config.getBoolean("options",
				"ActiveGateExplosion", true)));

		Property enableAnalytics = config.get("options", "enableAnalytics", true);
		configValues.add(new ConfigValue<Boolean>("enableAnalytics", config.getBoolean("options", "enableAnalytics",
				true)));
		enableAnalytics.comment = "Submit anonymous usage statistic data. (true/false)";

		if (version != previousVersion && enableAnalytics.getBoolean(true))
			analyticsHelper.start();
		versionHelper.start();
	}

	void registerRecipes() {
		LanteaCraft.getLogger().log(Level.DEBUG, "Registering LanteaCraft recipes...");
		if (config.getBoolean("options", "allowCraftingNaquadah", false)) {
			// TODO: moved to module -> core
		}
		if (config.getBoolean("options", "allowCraftingCrystals", false)) {
			// TODO: moved to module -> stargate
		}
	}

	public ConfigValue<?> getConfigValue(String name) {
		if (BuildInfo.ASSET_DEBUGGING)
			LanteaCraft.getLogger().log(Level.DEBUG, "Fetching configuration value `" + name + "`");
		for (ConfigValue<?> item : configValues)
			if (item.getName().equalsIgnoreCase(name))
				return item;
		return null;
	}

	@SuppressWarnings("unchecked")
	public int getRenderMode() {
		int mode = ((ConfigValue<Integer>) getConfigValue("renderQuality")).getValue();
		if (mode == 16 || mode == 32 || mode == 64 || mode == 128)
			return mode;
		return 32;
	}

	@SuppressWarnings("unchecked")
	public boolean isUsingModels() {
		return ((ConfigValue<Boolean>) getConfigValue("renderUseModels")).getValue();
	}

	@SuppressWarnings("unchecked")
	public boolean doExplosion() {
		return ((ConfigValue<Boolean>) getConfigValue("doGateExplosion")).getValue();
	}

	public int addVillager(int id, String name, ResourceLocation skin) {
		LanteaCraft.getLogger().log(Level.DEBUG, "Adding villager ID " + id + " with name " + name);
		registeredVillagers.put(name, new VillagerMapping(id, skin));
		return id;
	}

	public void addContainer(int id, Class<? extends Container> cls) {
		LanteaCraft.getLogger().log(Level.DEBUG,
				"Registering container with ID " + id + ", class " + cls.getCanonicalName());
		registeredContainers.put(id, cls);
	}

	public Class<? extends Container> getContainer(int id) {
		return registeredContainers.get(id);
	}

	public Object getGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		return null;
	}

	public Class<? extends GuiScreen> getGUI(int id) {
		return registeredGUIs.get(id);
	}

	public int getVillagerID(String name) {
		VillagerMapping villager = registeredVillagers.get(name);
		if (villager != null)
			return villager.villagerID;
		return 0;
	}

	public void handlePacket(ModPacket modPacket, EntityPlayer player) {
		if (modPacket.getPacketIsForServer())
			serverPacketHandler.handlePacket(modPacket, player);
		else
			return;
	}

	public ConfigurationHelper getConfig() {
		return config;
	}

	public ResourceLocation fetchResource(String resource) {
		if (!resourceCache.containsKey(resource))
			resourceCache.put(resource, new ResourceLocation(LanteaCraft.getAssetKey(), resource));
		return resourceCache.get(resource);
	}

	public void onServerStarting(FMLServerStartingEvent e) {
		if (worldLogger != null) {
			worldLogger.close();
			worldLogger = null;
		}
		try {
			File base = new File(new File(".").getCanonicalPath());
			File datadir = new File(base, "saves/" + e.getServer().getFolderName() + "/data").getAbsoluteFile();
			File logfile = new File(datadir, "LanteaCraft.log");
			worldLogger = new WorldLog(logfile);
			LanteaCraft.getLogger().log(Level.INFO, String.format("WorldLog starting: %s", logfile.toString()));
			worldLogger.open();
		} catch (IOException ioex) {
			LanteaCraft.getLogger().log(Level.WARN, "Failed to resolve paths for WorldLog.", ioex);
		}
	}

	public void onServerStopping(FMLServerStoppingEvent e) {
		if (worldLogger != null) {
			LanteaCraft.getLogger().log(Level.INFO, "WorldLog shutting down...");
			worldLogger.close();
			worldLogger = null;
		}
	}

	public WorldLog getWorldLog() {
		return worldLogger;
	}

	public RemoteChunkLoading getRemoteChunkManager() {
		return remoteChunkManager;
	}

	public StargateConnectionManager getConnectionManager() {
		return stargateConnectionManager;
	}

}
