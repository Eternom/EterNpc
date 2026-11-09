package fr.eternom.eterNpc.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Clic droit d'un joueur sur un PNJ (une fois par clic, main principale). Le plugin qui possède le PNJ y répond ; les PNJ
 * d'EterNpc lui-même (« eternpc:... ») lancent leurs actions avant cet événement.
 */
public class NpcClickEvent extends Event {

    /** Le clic : droit, ou droit accroupi. Depuis 2.0.0 (le clic gauche ne sert qu'aux PNJ d'EterNpc, sans événement). */
    public enum Click { RIGHT, SHIFT_RIGHT }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String npc;
    private final Click click;
    private final long placement;

    /** Clic droit (avant 2.0.0). */
    public NpcClickEvent(Player player, String npc) {
        this(player, npc, Click.RIGHT, -1);
    }

    public NpcClickEvent(Player player, String npc, Click click, long placement) {
        this.player = player;
        this.npc = npc;
        this.click = click;
        this.placement = placement;
    }

    public Player getPlayer() {
        return player;
    }

    /** Identifiant du PNJ (« plugin:nom »). */
    public String getNpc() {
        return npc;
    }

    /** Le clic (accroupi ou non : getPlayer().isSneaking() marche aussi). */
    public Click getClick() {
        return click;
    }

    /** Identifiant de l'emplacement cliqué (-1 si inconnu). */
    public long getPlacement() {
        return placement;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
