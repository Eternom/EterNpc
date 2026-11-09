package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterNpc.api.NpcClickEvent;
import fr.eternom.eterNpc.module.own.OwnNpc;
import fr.eternom.eterNpc.module.own.OwnNpcs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Clics sur les PNJ : un PNJ d'EterNpc lance ses actions (droit, droit accroupi, gauche) ; tout PNJ déclenche
 * NpcClickEvent au clic droit pour le plugin qui le possède. Les PNJ sont invincibles, apparaissent avec leur chunk et
 * disparaissent quand leur plugin s'arrête ; ceux réservés à une permission sont montrés à l'arrivée.
 */
public class NpcListener implements Listener {

    private final NpcService npcs;
    private final OwnNpcs own;

    public NpcListener(NpcService npcs, OwnNpcs own) {
        this.npcs = npcs;
        this.own = own;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        npcs.npcOf(event.getRightClicked()).ifPresent(npc -> {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return; // un seul clic, pas deux (main principale et seconde main)
            }
            Player player = event.getPlayer();
            Entity entity = event.getRightClicked();
            long placement = npcs.spawner().placementOf(entity);
            boolean shift = player.isSneaking();
            if (npc.startsWith(OwnNpc.PREFIX)) {
                own.click(player, entity, npc, shift ? OwnNpc.Click.SHIFT : OwnNpc.Click.RIGHT, placement);
            }
            Bukkit.getPluginManager().callEvent(new NpcClickEvent(player, npc,
                    shift ? NpcClickEvent.Click.SHIFT_RIGHT : NpcClickEvent.Click.RIGHT, placement));
        });
    }

    /** Invincibles, même face à un joueur en créatif (setInvulnerable ne l'arrête pas) ; un coup = clic gauche. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (npcs.npcOf(event.getEntity()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            String npc = npcs.npcOf(event.getEntity()).get();
            if (npc.startsWith(OwnNpc.PREFIX)) {
                own.click(player, event.getEntity(), npc, OwnNpc.Click.LEFT, npcs.spawner().placementOf(event.getEntity()));
            }
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
    public void onJoin(PlayerJoinEvent event) {
        npcs.spawner().refreshVisibility(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        npcs.spawner().forget(event.getPlayer().getUniqueId());
        own.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        npcs.undefineAll(event.getPlugin());
    }
}
