package fr.eternom.eterNpc.module.own;

import com.google.gson.JsonObject;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.cache.NetworkBus;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterNpc.api.NpcLook;
import fr.eternom.eterNpc.module.npc.NpcService;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Les PNJ d'EterNpc (« eternpc:<nom> ») : créés et réglés en jeu, gardés en base, communs au réseau (rechargés partout
 * quand l'un change : bus « eternpc », message « own »). Un clic lance leurs actions d'EterLib, après leur dialogue :
 * permission, délai, geste, son, puis le dialogue (lignes et choix cliquables) ou les actions du clic.
 * Un emplacement peut avoir ses propres actions de clic droit. Thread principal sauf mention.
 */
public class OwnNpcs {

    private static final String RELOAD = "own";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    /** Délai minimal entre deux clics d'un joueur sur un PNJ (doubles clics). */
    private static final Duration MIN_COOLDOWN = Duration.ofMillis(500);
    private static final long STATS_TICKS = 60 * 20;
    private static final Duration BUBBLE = Duration.ofSeconds(4);

    private final JavaPlugin plugin;
    private final OwnNpcRepository repository;
    private final NpcService npcs;
    private final NetworkBus bus;
    private final Messages messages;
    private final Map<String, OwnNpc> byName = new ConcurrentHashMap<>();
    /** Clics pas encore comptés en base (statistiques). */
    private final Map<String, Long> clicks = new ConcurrentHashMap<>();
    private final NpcEditor editor;

    public OwnNpcs(JavaPlugin plugin, OwnNpcRepository repository, NpcService npcs, NetworkBus bus, Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.npcs = npcs;
        this.bus = bus;
        this.messages = messages;
        this.editor = new NpcEditor(this);
    }

    /** L'éditeur d'un PNJ (staff). */
    public void openEditor(Player admin, String name) {
        editor.open(admin, name);
    }

    /** La liste de tous les PNJ connus ici (staff). */
    public void openList(Player admin) {
        admin.openInventory(new NpcListMenu(this, editor, admin, 0).getInventory());
    }

    public void start() {
        bus.on(RELOAD, data -> reload());
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushClicks, STATS_TICKS, STATS_TICKS);
        reload();
    }

    public void stop() {
        flushClicks();
    }

    /** Relit tous les PNJ en base et les (re)définit ici. */
    public void reload() {
        Tasks.async(plugin, () -> {
            List<OwnNpc> all = repository.all();
            Bukkit.getScheduler().runTask(plugin, () -> {
                byName.keySet().stream().filter(name -> all.stream().noneMatch(npc -> npc.name().equals(name))).toList()
                        .forEach(name -> {
                            byName.remove(name);
                            npcs.undefine(OwnNpc.PREFIX + name);
                        });
                all.forEach(this::show);
            });
        }, "PNJ d'EterNpc illisibles");
    }

    public Optional<OwnNpc> get(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    /** « eternpc:guilde » ou « guilde » -> le PNJ. */
    public Optional<OwnNpc> byId(String id) {
        return get(id.startsWith(OwnNpc.PREFIX) ? id.substring(OwnNpc.PREFIX.length()) : id);
    }

    public List<String> names() {
        return byName.keySet().stream().sorted().toList();
    }

    public static boolean validName(String name) {
        return NAME.matcher(name).matches();
    }

    // ---------- Création et changements ----------

    /** Crée le PNJ (une fois pour tout le réseau) ; then reçoit true s'il a été créé. */
    public void create(OwnNpc npc, java.util.function.Consumer<Boolean> then) {
        Tasks.async(plugin, () -> {
            boolean created = repository.create(npc);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (created) {
                    show(npc);
                    changed();
                }
                then.accept(created);
            });
        }, "PNJ " + npc.name() + " non créé");
    }

    /** Enregistre un changement : appliqué ici tout de suite, puis sur les autres serveurs. */
    public void save(OwnNpc npc) {
        show(npc);
        Tasks.async(plugin, () -> {
            repository.save(npc);
            changed();
        }, "PNJ " + npc.name() + " non enregistré");
    }

    /** Supprime le PNJ et tous ses emplacements, partout. Thread principal ; done ensuite. */
    public void delete(String name, Runnable done) {
        byName.remove(name);
        npcs.undefine(OwnNpc.PREFIX + name);
        Tasks.async(plugin, () -> {
            repository.delete(name);
            npcs.deletePlacements(OwnNpc.PREFIX + name);
            changed();
            Bukkit.getScheduler().runTask(plugin, done);
        }, "PNJ " + name + " non supprimé");
    }

    /** Apparence et allure données au spawner. */
    private void show(OwnNpc npc) {
        byName.put(npc.name(), npc);
        Component description = npc.description().isBlank() ? null : render(npc.description());
        npcs.define(plugin, new NpcLook(npc.id(), render(npc.display()), description, blank(npc.skin()),
                npc.textureValue() == null || npc.textureValue().isBlank() ? null
                        : new NpcLook.Texture(npc.textureValue(), npc.textureSignature())));
        npcs.style(npc.id(), npc.style());
    }

    /** « lang:clé » : le texte de lang/ (names.<clé>, langue par défaut du serveur) ; sinon du MiniMessage. */
    Component render(String text) {
        if (text.startsWith("lang:")) {
            return messages.get(Bukkit.getConsoleSender(), "names." + text.substring(5));
        }
        return messages.render(text, TagResolver.empty());
    }

    private void changed() {
        bus.publish(RELOAD, new JsonObject(),
                () -> plugin.getLogger().warning("Redis injoignable : les autres serveurs verront ce PNJ après /eternpc reload"));
    }

    // ---------- Clics ----------

    /**
     * Un clic sur un PNJ d'EterNpc : permission, délai, geste, son ; puis le dialogue (s'il y en a un, au clic droit),
     * sinon les actions du clic (celles de l'emplacement en priorité, au clic droit).
     */
    public void click(Player player, Entity entity, String id, OwnNpc.Click click, long placementId) {
        Optional<OwnNpc> found = byId(id);
        if (found.isEmpty()) {
            return;
        }
        OwnNpc npc = found.get();
        if (npc.permission() != null && !npc.permission().isBlank() && !player.hasPermission(npc.permission())) {
            messages.send(player, "npc.no-permission");
            return;
        }
        Duration cooldown = npc.cooldown() > 0 ? Duration.ofSeconds(npc.cooldown()) : MIN_COOLDOWN;
        List<String> actions = npc.actions(click);
        if (click != OwnNpc.Click.LEFT) {
            Optional<Placement> placement = npcs.spawner().placement(placementId);
            if (placement.isPresent() && placement.get().actions() != null && !placement.get().actions().isBlank()) {
                actions = List.of(placement.get().actions().split("\n"));
            }
        }
        Dialogue dialogue = click == OwnNpc.Click.RIGHT ? Dialogue.parse(npc.dialogue()) : Dialogue.NONE;
        if (actions.isEmpty() && dialogue.isEmpty()) {
            return;
        }
        if (!cooldownOk(player, npc, cooldown)) {
            return;
        }
        clicks.merge(npc.name(), 1L, Long::sum);
        npcs.spawner().gesture(entity);
        if (!npc.sound().isBlank()) {
            try {
                player.playSound(Sound.sound(Key.key(npc.sound()), Sound.Source.NEUTRAL, 1, 1));
            } catch (RuntimeException ignored) {
                // son illisible : ignoré
            }
        }
        if (!dialogue.isEmpty()) {
            speak(player, npc, dialogue, actions);
        } else {
            EterLib.get().getActions().run(player, actions);
        }
    }

    /** Délai par joueur et par PNJ (doubles clics, ou délai réglé). */
    private final Map<String, Long> lastClick = new ConcurrentHashMap<>();

    private boolean cooldownOk(Player player, OwnNpc npc, Duration cooldown) {
        String key = player.getUniqueId() + "|" + npc.name();
        long now = System.currentTimeMillis();
        Long until = lastClick.get(key);
        if (until != null && until > now) {
            if (cooldown.compareTo(MIN_COOLDOWN) > 0) {
                messages.actionBar(player, "npc.cooldown", "time", String.valueOf(Math.max(1, (until - now + 999) / 1000)));
            }
            return false;
        }
        lastClick.put(key, now + cooldown.toMillis());
        return true;
    }

    public void forget(UUID player) {
        String prefix = player + "|";
        lastClick.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Le dialogue : chaque ligne dans le chat (nom du PNJ devant) et la première en bulle au-dessus de lui ; puis les choix
     * cliquables (chacun lance sa chaîne), ou, sans choix, les actions du clic.
     */
    private void speak(Player player, OwnNpc npc, Dialogue dialogue, List<String> actions) {
        Component name = render(npc.display());
        Map<String, String> values = Map.of("{player}", player.getName());
        for (String line : dialogue.lines()) {
            String text = replace(line, values);
            player.sendMessage(messages.render(messages.raw(player, "npc.dialogue.line") == null ? "<name> <dark_gray>»</dark_gray> <text>"
                    : messages.raw(player, "npc.dialogue.line"), TagResolver.resolver(Placeholder.component("name", name),
                    Placeholder.component("text", messages.render(text, TagResolver.empty())))));
        }
        if (!dialogue.lines().isEmpty()) {
            npcs.say(player, npc.id(), messages.render(replace(dialogue.lines().getFirst(), values), TagResolver.empty()), BUBBLE);
        }
        if (dialogue.choices().isEmpty()) {
            EterLib.get().getActions().run(player, actions);
            return;
        }
        Component choices = Component.empty();
        for (Dialogue.Choice choice : dialogue.choices()) {
            Component button = messages.get(player, "npc.dialogue.choice", TagResolver.resolver(Placeholder.component("choice",
                            messages.render(replace(choice.label(), values), TagResolver.empty()))))
                    .hoverEvent(HoverEvent.showText(messages.get(player, "npc.dialogue.choice-hover")))
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clicker) {
                            Bukkit.getScheduler().runTask(plugin, () -> EterLib.get().getActions().run(clicker, choice.actions()));
                        }
                    }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build()));
            choices = choices.append(button).append(Component.space());
        }
        player.sendMessage(choices);
    }

    private static String replace(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /** Téléporte le joueur à l'emplacement de ce PNJ le plus proche, sur ce serveur ; false s'il n'y en a pas. */
    public boolean teleport(Player player, String id) {
        org.bukkit.Location here = player.getLocation();
        Optional<Placement> nearest = npcs.spawner().placementsOf(id).stream()
                .filter(placement -> Bukkit.getWorld(placement.world()) != null)
                .min(java.util.Comparator.comparingDouble(placement -> placement.world().equals(here.getWorld().getName())
                        ? Math.pow(here.getX() - placement.x(), 2) + Math.pow(here.getY() - placement.y(), 2) + Math.pow(here.getZ() - placement.z(), 2)
                        : Double.MAX_VALUE / 2));
        nearest.ifPresent(placement -> player.teleport(new org.bukkit.Location(Bukkit.getWorld(placement.world()),
                placement.x(), placement.y(), placement.z(), placement.yaw(), 0)));
        return nearest.isPresent();
    }

    // ---------- Statistiques ----------

    /** Tâche de fond : les clics comptés partent en base. */
    private void flushClicks() {
        for (String name : List.copyOf(clicks.keySet())) {
            Long count = clicks.remove(name);
            if (count != null && count > 0) {
                try {
                    repository.addClicks(name, count);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Clics du PNJ " + name + " non comptés : " + e.getMessage());
                }
            }
        }
    }

    /** Clics en base plus ceux pas encore écrits. */
    public long clicksOf(OwnNpc npc) {
        return npc.clicks() + clicks.getOrDefault(npc.name(), 0L);
    }

    public Messages messages() {
        return messages;
    }

    public NpcService npcs() {
        return npcs;
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text;
    }

}
