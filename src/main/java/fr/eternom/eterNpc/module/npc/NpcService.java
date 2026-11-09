package fr.eternom.eterNpc.module.npc;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterNpc.api.NpcApi;
import fr.eternom.eterNpc.api.NpcLook;
import fr.eternom.eterNpc.api.NpcStyle;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * L'API des PNJ : les apparences données par les plugins (en mémoire), les emplacements de ce serveur (en base,
 * relus quand un autre serveur en change un : bus réseau d'EterNpc, message "reload").
 */
public class NpcService implements NpcApi {

    private static final String RELOAD = "reload";
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,31}:[a-z0-9_.-]{1,32}");

    private final JavaPlugin plugin;
    private final PlacementRepository repository;
    private final NpcSpawner spawner;
    private final NetworkBus bus;
    /** Portée des emplacements de ce serveur : son nom, LOBBIES sur un lobby, RESOURCE sur un monde ressource. */
    private final String scope;
    /** PNJ -> plugin qui l'a défini. */
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    public NpcService(JavaPlugin plugin, PlacementRepository repository, NpcSpawner spawner, NetworkBus bus, String scope) {
        this.plugin = plugin;
        this.repository = repository;
        this.spawner = spawner;
        this.bus = bus;
        this.scope = scope;
    }

    public void start() {
        spawner.start();
        bus.on(RELOAD, data -> reload()); // le serveur d'origine a déjà appliqué sa propre modification
        reload();
    }

    /** Relit les emplacements de ce serveur. */
    public void reload() {
        Tasks.async(plugin, () -> {
            List<Placement> placements = repository.of(scope);
            Bukkit.getScheduler().runTask(plugin, () -> spawner.loadPlacements(placements));
        }, "Emplacements des PNJ illisibles");
    }

    @Override
    public void define(Plugin owner, NpcLook look) {
        if (!ID.matcher(look.id()).matches()) {
            throw new IllegalArgumentException("Identifiant de PNJ invalide (plugin:nom en minuscules) : " + look.id());
        }
        owners.put(look.id(), owner.getName());
        spawner.define(look);
    }

    @Override
    public void style(String id, NpcStyle style) {
        spawner.style(id, style);
    }

    @Override
    public void undefine(String id) {
        owners.remove(id);
        spawner.undefine(id);
    }

    /** Un plugin s'arrête : ses PNJ disparaissent. */
    public void undefineAll(Plugin owner) {
        owners.entrySet().stream().filter(entry -> entry.getValue().equals(owner.getName())).map(Map.Entry::getKey).toList()
                .forEach(this::undefine);
    }

    /** Le plugin qui a défini ce PNJ ici, s'il est défini. */
    public Optional<String> owner(String id) {
        return Optional.ofNullable(owners.get(id));
    }

    @Override
    public void place(String id, Location location, Runnable done) {
        String world = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        Tasks.async(plugin, () -> {
            long placementId = repository.add(id, scope, world, x, y, z, yaw);
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.addPlacement(new Placement(placementId, id, scope, world, x, y, z, yaw));
                changed(); // sur un lobby : les autres lobbys l'affichent aussi
                done.run();
            });
        }, "PNJ " + id + " non placé");
    }

    /** Déplace un emplacement ici (même PNJ, mêmes actions). Thread principal. */
    public void move(Placement placement, Location location, Runnable done) {
        String world = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        Tasks.async(plugin, () -> {
            repository.move(placement.id(), world, x, y, z, yaw);
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.removePlacement(placement.id());
                spawner.addPlacement(new Placement(placement.id(), placement.npc(), placement.scope(), world, x, y, z, yaw,
                        placement.actions()));
                changed();
                done.run();
            });
        }, "Emplacement du PNJ " + placement.npc() + " non déplacé");
    }

    /** Actions propres à un emplacement (null : celles du PNJ). Thread principal. */
    public void setPlacementActions(Placement placement, String actions, Runnable done) {
        Tasks.async(plugin, () -> {
            repository.setActions(placement.id(), actions);
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.addPlacement(new Placement(placement.id(), placement.npc(), placement.scope(), placement.world(),
                        placement.x(), placement.y(), placement.z(), placement.yaw(), actions));
                changed();
                done.run();
            });
        }, "Actions de l'emplacement non enregistrées");
    }

    @Override
    public void removeNearest(Location location, double radius, Predicate<String> which, Consumer<Optional<String>> done) {
        Optional<Placement> nearest = spawner.nearest(location, radius, which);
        if (nearest.isEmpty()) {
            done.accept(Optional.empty());
            return;
        }
        Placement placement = nearest.get();
        Tasks.async(plugin, () -> {
            repository.remove(placement.id());
            Bukkit.getScheduler().runTask(plugin, () -> {
                spawner.removePlacement(placement.id());
                changed();
                done.accept(Optional.of(placement.npc()));
            });
        }, "Emplacement du PNJ " + placement.npc() + " non retiré");
    }

    @Override
    public void deletePlacements(String id) {
        repository.removeNpc(id);
        Bukkit.getScheduler().runTask(plugin, this::reload);
        changed();
    }

    @Override
    public int deletePlacementsByPrefix(String prefix) {
        int removed = repository.removeByPrefix(prefix);
        if (removed > 0) {
            Bukkit.getScheduler().runTask(plugin, this::reload);
            changed();
        }
        return removed;
    }

    @Override
    public void importPlacement(String id, String scope, String world, double x, double y, double z, float yaw) {
        repository.add(id, scope, world, x, y, z, yaw);
        Bukkit.getScheduler().runTask(plugin, this::reload);
        changed();
    }

    @Override
    public Optional<String> npcOf(Entity entity) {
        return spawner.npcOf(entity);
    }

    @Override
    public int placedHere(String id) {
        return spawner.count(id);
    }

    @Override
    public void marker(Player viewer, String id, Component marker) {
        spawner.marker(viewer, id, marker);
    }

    @Override
    public void say(Player viewer, String id, Component text, Duration duration) {
        spawner.bubble(viewer, id, text, duration.toMillis());
    }

    /** Tous les emplacements d'un PNJ sur le réseau. Bloquant. */
    public List<Placement> placementsOnNetwork(String id) {
        return repository.ofNpc(id);
    }

    /** Tous les emplacements du réseau. Bloquant. */
    public List<Placement> allPlacements() {
        return repository.all();
    }

    /** Supprime un emplacement par son identifiant (nettoyage). Bloquant. */
    public void removePlacement(long id) {
        repository.remove(id);
    }

    public String scope() {
        return scope;
    }

    public NpcSpawner spawner() {
        return spawner;
    }

    /** Prévient les autres serveurs qu'un emplacement a changé. */
    public void changed() {
        bus.publish(RELOAD, new JsonObject(),
                () -> plugin.getLogger().warning("Redis injoignable : les autres serveurs verront ce PNJ après /eternpc reload"));
    }
}
