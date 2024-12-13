package au.com.addstar.bouncypads;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BouncyPads extends JavaPlugin implements Listener {
    private final List<Player> bouncing = new ArrayList<>();
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
	}

	public void onEnable() {
		loadConfig();
		Bukkit.getPluginManager().registerEvents(this, this);
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

        Set<String> pads = conf.getConfigurationSection("pads").getKeys(false);
		for (String pname : pads) {
			try {
				PadType pad = new PadType();
				pad.name = pname;

				// Allow for "ANY" block (wildcard)
				String tmp = conf.getString("pads." + pname + ".top").toUpperCase();
				pad.top = (tmp.equals("ANY")) ?  null : Material.valueOf(tmp);

				tmp = conf.getString("pads." + pname + ".middle").toUpperCase();
				pad.middle = (tmp.equals("ANY")) ?  null : Material.valueOf(tmp);

				tmp = conf.getString("pads." + pname + ".bottom").toUpperCase();
				pad.bottom = (tmp.equals("ANY")) ?  null : Material.valueOf(tmp);

				pad.multiplier = conf.getDouble("pads." + pname + ".multiplier", 1);
				pad.velocity = conf.getDouble("pads." + pname + ".velocity", 3);
                String effect = conf.getString("pads." + pname + ".effect").toUpperCase();
                try {
                    pad.effect = Effect.valueOf(effect);
                } catch (IllegalArgumentException e) {
                    this.getLogger().warning("Invalid effect \"" + effect + "\" for \"" + pname + "\"!");
                    pad.effect = Effect.CLICK1;
                }
				pad.msg = conf.getString("pads." + pname + ".message");

				String sound = conf.getString("pads." + pname + ".sound").toUpperCase();
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
			Debug("  top       : " + ((p.top == null) ? "ANY" : p.top));
			Debug("  middle    : " + ((p.middle == null) ? "ANY" : p.middle));
			Debug("  bottom    : " + ((p.bottom == null) ? "ANY" : p.bottom));
			Debug("  multiplier: " + p.multiplier);
			Debug("  velocity  : " + p.velocity);
			Debug("  sound     : " + p.sound);
			Debug("  effect    : " + p.effect);
			Debug("  message   : " + p.msg);
		}
		
		return true;
	}
	
	private PadType getPadType(Player player) {
		Location loc = player.getLocation();
		Material pt = loc.getBlock().getType();
		Material pm = null; // cached inside loop
		Material pb = null; // cached inside loop

		// Avoid tiggering while flying above bouncy pads
		// NOTE: Does not avoid flying above non-solid blocks if player is inside the same block as the non-solid block
		if ((pt == Material.AIR) && (loc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid())) {
			loc.subtract(0, 1, 0);
			pt = loc.getBlock().getType();  
		}
		
		// Check the layers for relevant trigger combinations
		for (PadType pad : PadList) {
			if ((pad.top == null) || (pad.top == pt)) {
				if (pm == null) { pm = loc.subtract(0, 1, 0).getBlock().getType(); }		// cache the middle block type
				if ((pad.middle == null) || (pad.middle == pm)) {
					if (pad.bottom == null) { return pad; }		// Wildcard on bottom block, don't bother checking it
					if (pb == null) { pb = loc.subtract(0, 1, 0).getBlock().getType(); }	// cache the bottom block type
					if (pad.bottom == pb) {
						// We found a BouncyPad! Return it
						return pad;
					}
				}
			}
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
