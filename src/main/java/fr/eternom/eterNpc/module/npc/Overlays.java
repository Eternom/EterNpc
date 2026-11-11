package fr.eternom.eterNpc.module.npc;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Ce qui flotte au-dessus d'un PNJ pour UN joueur : un marqueur (« ! » quête prête...) gardé tant qu'il est connecté, ou
 * une bulle de texte quelques secondes. Ce sont des TextDisplay non enregistrés, visibles seulement par ce joueur,
 * recréés quand le Mannequin réapparaît et retirés avec lui. Thread principal.
 */
final class Overlays {

    /** Ce qu'un joueur voit au-dessus d'un PNJ. until : fin d'une bulle (0 : marqueur permanent). */
    private record Shown(Component text, long until) {
    }

    /** Hauteur au-dessus des pieds, pour une taille 1 (nom et description compris). */
    private static final double HEIGHT = 2.55;
    private static final double BUBBLE_EXTRA = 0.35;

    private final JavaPlugin plugin;
    /** PNJ -> joueur -> marqueur ; PNJ -> joueur -> bulle. */
    private final Map<String, Map<UUID, Shown>> markers = new HashMap<>();
    private final Map<String, Map<UUID, Shown>> bubbles = new HashMap<>();
    /** (emplacement, joueur, marqueur ou bulle) -> entité affichée. */
    private final Map<String, UUID> displays = new HashMap<>();

    Overlays(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void marker(Player viewer, String npc, Component marker) {
        Map<UUID, Shown> byPlayer = markers.computeIfAbsent(npc, id -> new HashMap<>());
        if (marker == null) {
            byPlayer.remove(viewer.getUniqueId());
        } else {
            byPlayer.put(viewer.getUniqueId(), new Shown(marker, 0));
        }
    }

    void bubble(Player viewer, String npc, Component text, long millis) {
        bubbles.computeIfAbsent(npc, id -> new HashMap<>()).put(viewer.getUniqueId(), new Shown(text, System.currentTimeMillis() + millis));
    }

    void forget(UUID viewer) {
        markers.values().forEach(byPlayer -> byPlayer.remove(viewer));
        bubbles.values().forEach(byPlayer -> byPlayer.remove(viewer));
        displays.entrySet().removeIf(entry -> {
            if (entry.getKey().contains("|" + viewer + "|")) {
                remove(entry.getValue());
                return true;
            }
            return false;
        });
    }

    /**
     * Met à jour ce qui flotte au-dessus d'un Mannequin vivant (appelé souvent) : crée ce qui manque, retire ce qui n'a
     * plus lieu d'être (marqueur retiré, bulle finie, joueur parti).
     */
    void refresh(long placement, String npc, Mannequin mannequin, double scale) {
        long now = System.currentTimeMillis();
        Map<UUID, Shown> wantedMarkers = markers.getOrDefault(npc, Map.of());
        Map<UUID, Shown> wantedBubbles = bubbles.getOrDefault(npc, Map.of());
        wantedBubbles.values().removeIf(shown -> shown.until() <= now);
        Location base = mannequin.getLocation();
        double top = HEIGHT * scale;
        sync(placement, "m", wantedMarkers, base.clone().add(0, top, 0));
        sync(placement, "b", wantedBubbles, base.clone().add(0, top + BUBBLE_EXTRA, 0));
    }

    /** Le Mannequin d'un emplacement disparaît : tout ce qui flotte au-dessus aussi. */
    void clear(long placement) {
        String prefix = placement + "|";
        for (Iterator<Map.Entry<String, UUID>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, UUID> entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                remove(entry.getValue());
                it.remove();
            }
        }
    }

    void clearAll() {
        displays.values().forEach(Overlays::remove);
        displays.clear();
    }

    private void sync(long placement, String kind, Map<UUID, Shown> wanted, Location at) {
        String prefix = placement + "|";
        // Retire ce qui n'est plus voulu
        for (Iterator<Map.Entry<String, UUID>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, UUID> entry = it.next();
            String key = entry.getKey();
            if (!key.startsWith(prefix) || !key.endsWith("|" + kind)) {
                continue;
            }
            UUID viewer = UUID.fromString(key.substring(prefix.length(), key.lastIndexOf('|')));
            if (!wanted.containsKey(viewer) || Bukkit.getPlayer(viewer) == null) {
                remove(entry.getValue());
                it.remove();
            }
        }
        // Crée ou met à jour ce qui est voulu
        wanted.forEach((viewer, shown) -> {
            Player player = Bukkit.getPlayer(viewer);
            if (player == null || !player.getWorld().equals(at.getWorld())) {
                return;
            }
            String key = prefix + viewer + "|" + kind;
            Entity existing = displays.containsKey(key) ? Bukkit.getEntity(displays.get(key)) : null;
            if (existing instanceof TextDisplay display && display.isValid()) {
                if (!Objects.equals(display.text(), shown.text())) {
                    display.text(shown.text());
                }
                // Le PNJ a pu changer de monde : pas de distance entre deux mondes
                if (!display.getWorld().equals(at.getWorld()) || display.getLocation().distanceSquared(at) > 0.01) {
                    display.teleport(at);
                }
                return;
            }
            TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setVisibleByDefault(false);
                spawned.setBillboard(Display.Billboard.CENTER);
                spawned.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
                spawned.setShadowed(true);
                spawned.text(shown.text());
            });
            player.showEntity(plugin, display);
            displays.put(key, display.getUniqueId());
        });
    }

    private static void remove(UUID entity) {
        Entity found = Bukkit.getEntity(entity);
        if (found != null) {
            found.remove();
        }
    }
}
