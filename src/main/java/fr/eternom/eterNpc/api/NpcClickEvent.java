package fr.eternom.eterNpc.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Clic droit d'un joueur sur un PNJ (une fois, main principale). Le plugin qui possède le PNJ y répond. */
public class NpcClickEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String npc;

    public NpcClickEvent(Player player, String npc) {
        this.player = player;
        this.npc = npc;
    }

    public Player getPlayer() {
        return player;
    }

    /** Identifiant du PNJ (« plugin:nom »). */
    public String getNpc() {
        return npc;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
