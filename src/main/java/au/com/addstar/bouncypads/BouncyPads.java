package au.com.addstar.bouncypads;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class BouncyPads extends JavaPlugin implements Listener {
    // Using a set avoids duplicates and provides O(1) contains/remove
    private final Set<Player> bouncing = new HashSet<>();
    private final List<PadType> PadList = new ArrayList<>();
    private boolean Debug = true;
	
	public static class PadType {
		String name;
		Material top;
		Material middle;
		Material bottom;
		double velocity;
		double multiplier;
		Sound sound;
		Effect effect;
		String msg;

		public String getName() {
			return name;
		}

		public double getVelocity() {
			return velocity;
		}

		public double getMultiplier() {
			return multiplier;
		}
	}

	public void onEnable() {
		if (!loadConfig())
			getLogger().warning("No pads configured");
		Bukkit.getPluginManager().registerEvents(this, this);
	}

	@Override
	public void onDisable() {
		bouncing.clear();
	}

    private void Debug(String msg) {
		if (Debug) {
			getLogger().info(msg);
		}
	}
	
	private boolean loadConfig() {
		FileConfiguration conf = this.getConfig();
		//conf.options().copyDefaults(true);
		//this.saveDefaultConfig();
		
		Debug = conf.getBoolean("debug", false);

		if (conf.getConfigurationSection("pads") == null)
			return false;

		Set<String> pads = conf.getConfigurationSection("pads").getKeys(false);
		for (String pname : pads) {
			try {
				PadType pad = new PadType();
				pad.name = pname;

				// Allow for "ANY" block (wildcard)
				String tmp = conf.getString("pads." + pname + ".top", "ANY");
				tmp = (tmp == null) ? "ANY" : tmp.toUpperCase();
				pad.top = (tmp.equals("ANY")) ?	 null : Material.valueOf(tmp);

				tmp = conf.getString("pads." + pname + ".middle", "ANY");
				tmp = (tmp == null) ? "ANY" : tmp.toUpperCase();
				pad.middle = (tmp.equals("ANY")) ?  null : Material.valueOf(tmp);

				tmp = conf.getString("pads." + pname + ".bottom", "ANY");
				tmp = (tmp == null) ? "ANY" : tmp.toUpperCase();
				pad.bottom = (tmp.equals("ANY")) ?  null : Material.valueOf(tmp);

				pad.multiplier = conf.getDouble("pads." + pname + ".multiplier", 1);
				pad.velocity = conf.getDouble("pads." + pname + ".velocity", 3);
		String effect = conf.getString("pads." + pname + ".effect", Effect.CLICK1.name());
		effect = (effect == null) ? Effect.CLICK1.name() : effect.toUpperCase();
		try {
		    pad.effect = Effect.valueOf(effect);
		} catch (IllegalArgumentException e) {
		    this.getLogger().warning("Invalid effect \"" + effect + "\" for \"" + pname + "\"!");
		    pad.effect = Effect.CLICK1;
		}
				pad.msg = conf.getString("pads." + pname + ".message");

				String sound = conf.getString("pads." + pname + ".sound", Sound.ENTITY_ENDER_DRAGON_HURT.name());
				sound = (sound == null) ? Sound.ENTITY_ENDER_DRAGON_HURT.name() : sound.toUpperCase();
				try {
					pad.sound = Sound.valueOf(sound);
				}
				catch (Exception e) {
					this.getLogger().warning("Invalid sound \"" + sound + "\" for \"" + pname + "\"!");
		    pad.sound = Sound.ENTITY_ENDER_DRAGON_HURT; // set this as default
				}
				PadList.add(pad);
			}
			catch (Exception e) {
				this.getLogger().warning("Unable to load pad configuration for \"" + pname + "\"!");
				e.printStackTrace();
			}
		}
		
		for (PadType p : PadList) {
			Debug("Pad config:");
			Debug("	 top	   : " + ((p.top == null) ? "ANY" : p.top));
			Debug("	 middle	   : " + ((p.middle == null) ? "ANY" : p.middle));
			Debug("	 bottom	   : " + ((p.bottom == null) ? "ANY" : p.bottom));
			Debug("	 multiplier: " + p.multiplier);
			Debug("	 velocity  : " + p.velocity);
			Debug("	 sound	   : " + p.sound);
			Debug("	 effect	   : " + p.effect);
			Debug("	 message   : " + p.msg);
		}
		
		return true;
	}
	private PadType getPadType(Player player) {
		// Work on block references to avoid mutating the Location object
		Block block = player.getLocation().getBlock();
		Material topMat = block.getType();

		// Avoid triggering while flying above bouncy pads
		if (topMat == Material.AIR && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
			block = block.getRelative(BlockFace.DOWN);
			topMat = block.getType();
		}

		Material middleMat = null;
		Material bottomMat = null;

		// Check the layers for relevant trigger combinations
		for (PadType pad : PadList) {
			if (pad.top != null && pad.top != topMat)
				continue;
			if (pad.middle != null) {
				if (middleMat == null)
					middleMat = block.getRelative(BlockFace.DOWN).getType();
				if (pad.middle != middleMat)
					continue;
			}
			if (pad.bottom != null) {
				if (bottomMat == null)
					bottomMat = block.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getType();
				if (pad.bottom != bottomMat)
					continue;
			}
			return pad;
		}
		return null;
	}
	
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		final Player player = event.getPlayer();
		PadType pad = getPadType(player);

		// Player is not on a BouncyPad, ignore it
		if (pad == null) return;

		// Don't handle if player is already bouncing
		if (!bouncing.contains(player)) {
			bouncing.add(player);
			player.playSound(player.getLocation(), pad.sound, 1, 1);
			player.playEffect(player.getLocation(), pad.effect, null);
			player.setVelocity(player.getLocation().getDirection().multiply(pad.multiplier));
			player.setVelocity(new Vector(player.getVelocity().getX(), pad.velocity, player.getVelocity().getZ()));
			if ((pad.msg != null) && (!pad.msg.isEmpty())) {
				player.sendMessage(ChatColor.translateAlternateColorCodes('&', pad.msg));
			}
	    Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> bouncing.remove(player), 5L);

			// Fire PlayerBouncedEvent (for other plugins to listen to)
			Bukkit.getPluginManager().callEvent(new PlayerBouncedEvent(player, pad));
		}
	}
}
