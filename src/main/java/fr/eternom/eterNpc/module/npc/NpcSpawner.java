package fr.eternom.eterNpc.module.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterNpc.api.NpcLook;
import fr.eternom.eterNpc.api.NpcStyle;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
 * être en double après un redémarrage. Chaque Mannequin porte l'identifiant de son PNJ et de son emplacement (données
 * persistantes de l'entité) pour reconnaître un clic. Un PNJ n'apparaît que s'il est défini et placé.
 * Son allure (NpcStyle) : équipement, pose, taille, regard, halo, et visibilité réservée à une permission.
 * Thread principal.
 */
public class NpcSpawner {

    private static final long LOOK_TICKS = 2;
    private static final long RESPAWN_TICKS = 5 * 20;
    private static final long OVERLAY_TICKS = 10;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final NamespacedKey npcKey;
    private final NamespacedKey placementKey;
    private final Overlays overlays;
    private final Map<String, NpcLook> looks = new ConcurrentHashMap<>();
    private final Map<String, NpcStyle> styles = new ConcurrentHashMap<>();
    private final Map<Long, Placement> placements = new ConcurrentHashMap<>();
    /** Emplacement -> Mannequin actuellement présent. */
    private final Map<Long, UUID> alive = new ConcurrentHashMap<>();

    public NpcSpawner(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.npcKey = new NamespacedKey(plugin, "npc");
        this.placementKey = new NamespacedKey(plugin, "placement");
        this.overlays = new Overlays(plugin);
    }

    /** Regard (tous les 2 ticks), marqueurs et bulles (toutes les 0,5 s), retour d'un PNJ disparu et visibilité (5 s). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::lookAtPlayers, LOOK_TICKS, LOOK_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOverlays, OVERLAY_TICKS, OVERLAY_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            placements.values().forEach(this::spawnIfLoaded);
            Bukkit.getOnlinePlayers().forEach(this::refreshVisibility);
        }, RESPAWN_TICKS, RESPAWN_TICKS);
    }

    /** Remplace les emplacements (démarrage, rechargement) ; les Mannequins des emplacements déplacés ou disparus s'en vont. */
    public void loadPlacements(List<Placement> fresh) {
        Map<Long, Placement> byId = new ConcurrentHashMap<>();
        fresh.forEach(placement -> byId.put(placement.id(), placement));
        for (Long id : List.copyOf(alive.keySet())) {
            Placement now = byId.get(id);
            if (now == null || !now.samePlace(placements.get(id))) {
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
        forEachAlive(look.id(), mannequin -> configure(mannequin, look));
        placements.values().stream().filter(placement -> placement.npc().equals(look.id())).forEach(this::spawnIfLoaded);
    }

    /** Allure d'un PNJ : appliquée tout de suite à ses Mannequins. */
    public void style(String id, NpcStyle style) {
        NpcStyle previous = styles.put(id, style);
        if (style.equals(previous)) {
            return;
        }
        forEachAlive(id, mannequin -> {
            applyStyle(mannequin, style);
            seat(mannequin, style);
        });
        Bukkit.getOnlinePlayers().forEach(this::refreshVisibility);
    }

    public NpcStyle styleOf(String id) {
        return styles.getOrDefault(id, NpcStyle.DEFAULT);
    }

    public void undefine(String id) {
        looks.remove(id);
        styles.remove(id);
        placements.values().stream().filter(placement -> placement.npc().equals(id)).forEach(placement -> despawn(placement.id()));
    }

    public boolean isDefined(String id) {
        return looks.containsKey(id);
    }

    public Optional<NpcLook> look(String id) {
        return Optional.ofNullable(looks.get(id));
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

    public Optional<Placement> placement(long id) {
        return Optional.ofNullable(placements.get(id));
    }

    public List<Placement> placementsOf(String npc) {
        return placements.values().stream().filter(placement -> placement.npc().equals(npc)).toList();
    }

    public int count(String npc) {
        return placementsOf(npc).size();
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

    /** Emplacement de ce Mannequin (-1 s'il est inconnu). */
    public long placementOf(Entity entity) {
        Long id = entity.getPersistentDataContainer().get(placementKey, PersistentDataType.LONG);
        return id == null ? -1 : id;
    }

    // ---------- Marqueurs et bulles ----------

    public void marker(Player viewer, String npc, net.kyori.adventure.text.Component marker) {
        overlays.marker(viewer, npc, marker);
    }

    public void bubble(Player viewer, String npc, net.kyori.adventure.text.Component text, long millis) {
        overlays.bubble(viewer, npc, text, millis);
    }

    public void forget(UUID viewer) {
        overlays.forget(viewer);
    }

    private void refreshOverlays() {
        alive.forEach((placementId, uuid) -> {
            Placement placement = placements.get(placementId);
            if (placement != null && Bukkit.getEntity(uuid) instanceof Mannequin mannequin) {
                overlays.refresh(placementId, placement.npc(), mannequin, styleOf(placement.npc()).scale());
            }
        });
    }

    // ---------- Visibilité ----------

    /** PNJ réservés à une permission : montrés ou cachés à ce joueur. */
    public void refreshVisibility(Player player) {
        alive.forEach((placementId, uuid) -> {
            Placement placement = placements.get(placementId);
            Entity entity = Bukkit.getEntity(uuid);
            if (placement == null || entity == null) {
                return;
            }
            String permission = styleOf(placement.npc()).permission();
            if (permission == null || player.hasPermission(permission)) {
                player.showEntity(plugin, entity);
            } else {
                player.hideEntity(plugin, entity);
            }
        });
    }

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
        overlays.clearAll();
    }

    /** Petit geste au clic : le bras se lève. */
    public void gesture(Entity entity) {
        if (entity instanceof Mannequin mannequin) {
            mannequin.swingMainHand();
        }
    }

    /**
     * Tête et corps tournés vers le joueur le plus proche (à moins de lookRange blocs, ni spectateur ni invisible),
     * sans bouger ; sinon, la direction de l'emplacement. Le même regard pour tous.
     */
    private void lookAtPlayers() {
        alive.forEach((placementId, uuid) -> {
            Placement placement = placements.get(placementId);
            if (placement == null || !(Bukkit.getEntity(uuid) instanceof Mannequin mannequin)) {
                return;
            }
            NpcStyle style = styleOf(placement.npc());
            float yaw = placement.yaw();
            float pitch = 0;
            if (style.look()) {
                Location eyes = mannequin.getEyeLocation();
                Optional<Player> nearest = eyes.getNearbyPlayers(style.lookRange()).stream()
                        .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
                        .filter(player -> !EterLib.get().getVanish().isVanished(player.getUniqueId()))
                        .min(Comparator.comparingDouble(player -> player.getEyeLocation().distanceSquared(eyes)));
                if (nearest.isPresent()) {
                    Vector toPlayer = nearest.get().getEyeLocation().toVector().subtract(eyes.toVector());
                    yaw = (float) Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
                    pitch = (float) Math.toDegrees(-Math.atan2(toPlayer.getY(), Math.hypot(toPlayer.getX(), toPlayer.getZ())));
                }
            }
            mannequin.setRotation(yaw, pitch);
            mannequin.setBodyYaw(yaw);
        });
    }

    private void forEachAlive(String npc, java.util.function.Consumer<Mannequin> action) {
        alive.forEach((id, uuid) -> {
            Placement placement = placements.get(id);
            if (placement != null && placement.npc().equals(npc) && Bukkit.getEntity(uuid) instanceof Mannequin mannequin) {
                action.accept(mannequin);
            }
        });
    }

    private void despawn(long placementId) {
        overlays.clear(placementId);
        UUID uuid = alive.remove(placementId);
        Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
        if (entity != null) {
            // Assis : son support invisible part avec lui
            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                vehicle.remove();
            }
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
        NpcStyle style = styleOf(placement.npc());
        Location location = new Location(world, placement.x(), placement.y(), placement.z(), placement.yaw(), 0);
        Mannequin mannequin = world.spawn(location, Mannequin.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setImmovable(true);
            spawned.setRemoveWhenFarAway(false);
            spawned.setVisibleByDefault(style.permission() == null);
            spawned.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, look.id());
            spawned.getPersistentDataContainer().set(placementKey, PersistentDataType.LONG, placement.id());
            configure(spawned, look);
            applyStyle(spawned, style);
        });
        if (!mannequin.isValid()) {
            // Apparition annulée par un autre plugin (protection du spawn, WorldGuard, interdiction des créatures...)
            logger.warning("PNJ " + look.id() + " : apparition annulée par un autre plugin en " + placement.world() + " "
                    + (int) placement.x() + " " + (int) placement.y() + " " + (int) placement.z()
                    + " (protection du spawn, WorldGuard, interdiction des créatures ?)");
            return;
        }
        alive.put(placement.id(), mannequin.getUniqueId());
        seat(mannequin, style);
        if (style.permission() != null) {
            Bukkit.getOnlinePlayers().stream().filter(player -> player.hasPermission(style.permission()))
                    .forEach(player -> player.showEntity(plugin, mannequin));
        }
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

    private static void applyStyle(Mannequin mannequin, NpcStyle style) {
        for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            Material material = style.equipment().get(slot);
            mannequin.getEquipment().setItem(slot, material == null ? null : ItemStack.of(material));
        }
        Pose pose = pose(style.pose());
        mannequin.setPose(pose != Pose.SITTING && Mannequin.validPoses().contains(pose) ? pose : Pose.STANDING, true);
        AttributeInstance scale = mannequin.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(style.scale());
        }
        mannequin.setGlowing(style.glow());
    }

    /**
     * Pose « assis » : Minecraft ne la permet pas à un Mannequin seul ; il monte donc sur un support invisible (porte-armure
     * marqueur) juste sous lui, et s'affiche assis sur sa chaise ou son banc. Une autre pose : il en descend.
     */
    private static void seat(Mannequin mannequin, NpcStyle style) {
        boolean sitting = pose(style.pose()) == Pose.SITTING;
        Entity vehicle = mannequin.getVehicle();
        if (!sitting) {
            if (vehicle instanceof ArmorStand) {
                mannequin.leaveVehicle();
                vehicle.remove();
            }
            return;
        }
        if (vehicle != null) {
            return;
        }
        Location at = mannequin.getLocation().subtract(0, 0.2, 0);
        ArmorStand seat = mannequin.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setSilent(true);
        });
        if (seat.isValid()) {
            seat.addPassenger(mannequin);
        }
    }

    static Pose pose(String name) {
        try {
            return name == null ? Pose.STANDING : Pose.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Pose.STANDING;
        }
    }

    private static double distanceSquared(Placement placement, Location location) {
        double dx = placement.x() - location.getX();
        double dy = placement.y() - location.getY();
        double dz = placement.z() - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
