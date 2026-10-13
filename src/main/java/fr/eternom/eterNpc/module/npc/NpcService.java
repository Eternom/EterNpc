package fr.eternom.eterNpc.module.npc;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterNpc.api.NpcApi;
import fr.eternom.eterNpc.api.NpcLook;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

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
    /** Portée des emplacements de ce serveur : son nom, ou LOBBIES sur un lobby. */
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
    public void undefine(String id) {
        owners.remove(id);
        spawner.undefine(id);
    }

    /** Un plugin s'arrête : ses PNJ disparaissent. */
    public void undefineAll(Plugin owner) {
        owners.entrySet().stream().filter(entry -> entry.getValue().equals(owner.getName())).map(Map.Entry::getKey).toList()
                .forEach(this::undefine);
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

    public NpcSpawner spawner() {
        return spawner;
    }

    /** Prévient les autres serveurs qu'un emplacement a changé. */
    private void changed() {
        bus.publish(RELOAD, new JsonObject(),
                () -> plugin.getLogger().warning("Redis injoignable : les autres serveurs verront ce PNJ après /eternpc reload"));
    }
}
