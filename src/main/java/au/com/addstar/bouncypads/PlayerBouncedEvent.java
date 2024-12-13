package au.com.addstar.bouncypads;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import au.com.addstar.bouncypads.BouncyPads.PadType;

public class PlayerBouncedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PadType pad;
    private final Location loc;

    public PlayerBouncedEvent(Player player, PadType pad) {
        this.player = player;
        this.pad = pad;
        this.loc = player.getLocation();
    }

    public Player getPlayer() {
        return player;
    }

    public PadType getPad() {
        return pad;
    }

    public Location getLocation() {
        return loc;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
