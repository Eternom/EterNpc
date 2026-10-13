package fr.eternom.eterNpc.module.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.eternom.eterNpc.api.NpcLook;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Les PNJ de CE serveur, en Mannequins natifs. Ils ne sont jamais enregistrés dans le monde (non persistants) :
 * ils apparaissent quand le chunk de leur emplacement se charge, disparaissent avec lui, et ne peuvent donc jamais
 * être en double après un redémarrage. Chaque Mannequin porte l'identifiant de son PNJ (données persistantes de
 * l'entité) pour reconnaître un clic. Un PNJ n'apparaît que s'il est défini (apparence donnée par son plugin) et
 * placé. Thread principal.
 */
public class NpcSpawner {

    private static final long LOOK_TICKS = 2;
    private static final long RESPAWN_TICKS = 5 * 20;
    private static final double LOOK_RANGE = 8;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final NamespacedKey npcKey;
    private final Map<String, NpcLook> looks = new ConcurrentHashMap<>();
    private final Map<Long, Placement> placements = new ConcurrentHashMap<>();
    /** Emplacement -> Mannequin actuellement présent. */
    private final Map<Long, UUID> alive = new ConcurrentHashMap<>();

    public NpcSpawner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.npcKey = new NamespacedKey(plugin, "npc");
    }

    /** Regard vers le joueur le plus proche (tous les 2 ticks) et retour d'un PNJ disparu (toutes les 5 s). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, LOOK_TICKS, LOOK_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> placements.values().forEach(this::spawnIfLoaded), RESPAWN_TICKS, RESPAWN_TICKS);
    }

    /** Remplace les emplacements (démarrage, rechargement) ; les Mannequins des emplacements disparus s'en vont. */
    public void loadPlacements(List<Placement> fresh) {
        Map<Long, Placement> byId = new ConcurrentHashMap<>();
        fresh.forEach(placement -> byId.put(placement.id(), placement));
        for (Long id : List.copyOf(alive.keySet())) {
            Placement now = byId.get(id);
            if (now == null || !now.equals(placements.get(id))) {
                despawn(id);
            }
        }
        placements.clear();
        placements.putAll(byId);
        placements.values().forEach(this::spawnIfLoaded);
    }

    /** Définit ou met à jour un PNJ : ses Mannequins changent d'apparence sans réapparaître. */
    public void define(NpcLook look) {
        NpcLook previous = looks.put(look.id(), look);
        if (look.equals(previous)) {
            return; // inchangé : pas de nouveau skin à charger
        }
        alive.forEach((id, uuid) -> {
            Placement placement = placements.get(id);
            if (placement != null && placement.npc().equals(look.id()) && Bukkit.getEntity(uuid) instanceof Mannequin mannequin) {
                configure(mannequin, look);
            }
        });
        placements.values().stream().filter(placement -> placement.npc().equals(look.id())).forEach(this::spawnIfLoaded);
    }

    public void undefine(String id) {
        looks.remove(id);
        placements.values().stream().filter(placement -> placement.npc().equals(id)).forEach(placement -> despawn(placement.id()));
    }

    public boolean isDefined(String id) {
        return looks.containsKey(id);
    }

    public List<String> ids() {
        return looks.keySet().stream().sorted().toList();
    }

    public void addPlacement(Placement placement) {
        placements.put(placement.id(), placement);
        spawnIfLoaded(placement);
    }

    public void removePlacement(long id) {
        placements.remove(id);
        despawn(id);
    }

    public int count(String npc) {
        return (int) placements.values().stream().filter(placement -> placement.npc().equals(npc)).count();
    }

    /** Emplacement le plus proche de location, dans un rayon de radius blocs, parmi les PNJ acceptés par which. */
    public Optional<Placement> nearest(Location location, double radius, Predicate<String> which) {
        return placements.values().stream()
                .filter(placement -> which.test(placement.npc()))
                .filter(placement -> placement.world().equals(location.getWorld().getName()))
                .filter(placement -> distanceSquared(placement, location) <= radius * radius)
                .min(Comparator.comparingDouble(placement -> distanceSquared(placement, location)));
    }

    /** Identifiant du PNJ représenté par cette entité, s'il en est un. */
    public Optional<String> npcOf(Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING));
    }

    /** Un chunk se charge : ses PNJ apparaissent. */
    public void onChunkLoad(Chunk chunk) {
        String world = chunk.getWorld().getName();
        placements.values().stream()
                .filter(placement -> placement.world().equals(world)
                        && (int) Math.floor(placement.x()) >> 4 == chunk.getX()
                        && (int) Math.floor(placement.z()) >> 4 == chunk.getZ())
                .forEach(this::spawnIfLoaded);
    }

    public void despawnAll() {
        List.copyOf(alive.keySet()).forEach(this::despawn);
    }

    /**
     * Tête et corps tournés vers le joueur le plus proche (à moins de LOOK_RANGE blocs), sans bouger ; sinon, la
     * direction de l'emplacement. Le même regard pour tous : un regard propre à chaque joueur demanderait des paquets.
     */
    private void lookAtPlayers() {
        alive.forEach((placementId, uuid) -> {
            Placement placement = placements.get(placementId);
            if (placement == null || !(Bukkit.getEntity(uuid) instanceof Mannequin mannequin)) {
                return;
            }
            Location eyes = mannequin.getEyeLocation();
            Optional<Player> nearest = eyes.getNearbyPlayers(LOOK_RANGE).stream()
                    .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                    .min(Comparator.comparingDouble(player -> player.getEyeLocation().distanceSquared(eyes)));
            float yaw = placement.yaw();
            float pitch = 0;
            if (nearest.isPresent()) {
                Vector toPlayer = nearest.get().getEyeLocation().toVector().subtract(eyes.toVector());
                yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
                pitch = (float) Math.toDegrees(-Math.atan2(toPlayer.getY(), Math.hypot(toPlayer.getX(), toPlayer.getZ())));
            }
            mannequin.setRotation(yaw, pitch);
            mannequin.setBodyYaw(yaw);
        });
    }

    private void despawn(long placementId) {
        UUID uuid = alive.remove(placementId);
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    private void spawnIfLoaded(Placement placement) {
        NpcLook look = looks.get(placement.npc());
        World world = Bukkit.getWorld(placement.world());
        if (look == null || world == null || !world.isChunkLoaded((int) Math.floor(placement.x()) >> 4, (int) Math.floor(placement.z()) >> 4)) {
            return;
        }
        UUID current = alive.get(placement.id());
        if (current != null && Bukkit.getEntity(current) != null && Objects.requireNonNull(Bukkit.getEntity(current)).isValid()) {
            return; // déjà là
        }
        Location location = new Location(world, placement.x(), placement.y(), placement.z(), placement.yaw(), 0);
        Mannequin mannequin = world.spawn(location, Mannequin.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setImmovable(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, look.id());
            configure(spawned, look);
        });
        if (!mannequin.isValid()) {
            // Apparition annulée par un autre plugin (protection du spawn, WorldGuard, interdiction des créatures...)
            logger.warning("PNJ " + look.id() + " : apparition annulée par un autre plugin en " + placement.world() + " "
                    + (int) placement.x() + " " + (int) placement.y() + " " + (int) placement.z()
                    + " (protection du spawn, WorldGuard, interdiction des créatures ?)");
            return;
        }
        alive.put(placement.id(), mannequin.getUniqueId());
    }

    private static void configure(Mannequin mannequin, NpcLook look) {
        mannequin.customName(look.name());
        mannequin.setCustomNameVisible(true);
        mannequin.setDescription(look.description());
        ResolvableProfile.Builder profile = ResolvableProfile.resolvableProfile();
        if (look.texture() != null) {
            profile.addProperty(new ProfileProperty("textures", look.texture().value(), look.texture().signature()));
        } else if (look.skin() != null) {
            profile.name(look.skin()); // skin de ce joueur, récupéré par le jeu
        }
        mannequin.setProfile(look.texture() == null && look.skin() == null ? Mannequin.defaultProfile() : profile.build());
    }

    private static double distanceSquared(Placement placement, Location location) {
        double dx = placement.x() - location.getX();
        double dy = placement.y() - location.getY();
        double dz = placement.z() - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
