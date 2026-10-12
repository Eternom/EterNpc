package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterNpc.api.NpcClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Clic droit sur un PNJ : NpcClickEvent pour le plugin qui le possède. Les PNJ sont invincibles, apparaissent avec
 * leur chunk et disparaissent quand leur plugin s'arrête.
 */
public class NpcListener implements Listener {

    private final NpcService npcs;

    public NpcListener(NpcService npcs) {
        this.npcs = npcs;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        npcs.npcOf(event.getRightClicked()).ifPresent(npc -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) { // un seul clic, pas deux (main principale et seconde main)
                Bukkit.getPluginManager().callEvent(new NpcClickEvent(event.getPlayer(), npc));
            }
        });
    }

    /** Invincibles, même face à un joueur en créatif (setInvulnerable ne l'arrête pas) ni au feu. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (npcs.npcOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombust(EntityCombustEvent event) {
        if (npcs.npcOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        npcs.spawner().onChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        npcs.undefineAll(event.getPlugin());
    }
}
