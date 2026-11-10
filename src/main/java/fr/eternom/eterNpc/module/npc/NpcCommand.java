package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import fr.eternom.eterNpc.module.own.OwnNpc;
import fr.eternom.eterNpc.module.own.OwnNpcs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /eternpc (staff, eternpc.admin) :
 * - menu · list · reload ;
 * - create <nom> (PNJ d'EterNpc, placé ici) · edit <nom> · copy <nom> <nouveau> · delete <nom> confirm ;
 * - place <pnj> · remove (le plus proche) · move (le plus proche vient ici) · tp <pnj> (son emplacement le plus proche ici) ;
 * - cleanup (emplacements de PNJ d'EterNpc supprimés) · cleanup <préfixe> confirm (ex : « etermarket: »).
 * Les plugins gardent leurs propres commandes pour leurs PNJ.
 */
public class NpcCommand implements TabExecutor {

    private static final List<String> ACTIONS = List.of("menu", "list", "reload", "create", "edit", "copy", "delete", "place",
            "remove", "move", "tp", "cleanup", "reputation");
    private static final double RADIUS = 5;

    private final JavaPlugin plugin;
    private final NpcService npcs;
    private final OwnNpcs own;
    private final Messages messages;

    public NpcCommand(JavaPlugin plugin, NpcService npcs, OwnNpcs own, Messages messages) {
        this.plugin = plugin;
        this.npcs = npcs;
        this.own = own;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        String arg = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
        switch (action) {
            case "list" -> {
                List<String> ids = npcs.spawner().ids();
                messages.send(sender, ids.isEmpty() ? "npc.list-empty" : "npc.list", "npcs", String.join(", ", ids.stream()
                        .map(id -> id + " (" + npcs.placedHere(id) + ")").toList()));
            }
            case "reload" -> {
                npcs.reload();
                own.reload();
                messages.send(sender, "npc.reloaded");
            }
            case "cleanup" -> cleanup(sender, args);
            case "reputation" -> reputation(sender, arg, args);
            case "delete" -> {
                if (arg == null || own.get(arg).isEmpty()) {
                    messages.send(sender, "npc.unknown", "npc", arg == null ? "?" : arg);
                } else if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    messages.send(sender, "npc.delete-confirm", "npc", arg);
                } else {
                    own.delete(arg, () -> messages.send(sender, "npc.deleted", "npc", arg));
                }
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "command.players-only");
                    return true;
                }
                player(player, action, arg, args);
            }
        }
        return true;
    }

    private void player(Player player, String action, String arg, String[] args) {
        switch (action) {
            case "menu" -> own.openList(player);
            case "create" -> {
                if (arg == null || !OwnNpcs.validName(arg)) {
                    messages.send(player, "npc.invalid-name");
                    return;
                }
                own.create(OwnNpc.create(arg), created -> {
                    if (!created) {
                        messages.send(player, "npc.exists", "npc", arg);
                        return;
                    }
                    messages.send(player, "npc.created", "npc", arg);
                    npcs.place(OwnNpc.PREFIX + arg, player.getLocation(), () -> own.openEditor(player, arg));
                });
            }
            case "edit" -> {
                if (arg == null) {
                    nearestOwn(player).ifPresentOrElse(placement -> own.openEditor(player, placement.npc().substring(OwnNpc.PREFIX.length())),
                            () -> messages.send(player, "npc.usage"));
                } else {
                    own.openEditor(player, arg.startsWith(OwnNpc.PREFIX) ? arg.substring(OwnNpc.PREFIX.length()) : arg);
                }
            }
            case "copy" -> {
                String target = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : null;
                Optional<OwnNpc> source = arg == null ? Optional.empty() : own.get(arg);
                if (source.isEmpty() || target == null || !OwnNpcs.validName(target)) {
                    messages.send(player, "npc.copy-usage");
                    return;
                }
                own.create(source.get().copy(target), created -> {
                    if (!created) {
                        messages.send(player, "npc.exists", "npc", target);
                        return;
                    }
                    own.save(source.get().copy(target));
                    npcs.place(OwnNpc.PREFIX + target, player.getLocation(), () -> messages.send(player, "npc.copied",
                            "npc", arg, "target", target));
                });
            }
            case "place" -> {
                String id = arg == null ? null : full(arg);
                if (id == null || !npcs.spawner().isDefined(id)) {
                    messages.send(player, "npc.unknown", "npc", arg == null ? "?" : arg);
                } else {
                    npcs.place(id, player.getLocation(), () -> messages.send(player, "npc.placed", "npc", id));
                }
            }
            case "remove" -> npcs.removeNearest(player.getLocation(), RADIUS, npc -> true, removed -> removed.ifPresentOrElse(
                    id -> messages.send(player, "npc.removed", "npc", id),
                    () -> messages.send(player, "npc.none-near", "radius", String.valueOf((int) RADIUS))));
            case "move" -> npcs.spawner().nearest(player.getLocation(), arg == null ? RADIUS * 4 : RADIUS * 4,
                    id -> arg == null || id.equals(full(arg))).ifPresentOrElse(
                    placement -> npcs.move(placement, player.getLocation(), () -> messages.send(player, "npc.moved", "npc", placement.npc())),
                    () -> messages.send(player, "npc.none-near", "radius", String.valueOf((int) (RADIUS * 4))));
            case "tp" -> {
                if (arg == null || !own.teleport(player, full(arg))) {
                    messages.send(player, "npc.not-here", "npc", arg == null ? "?" : arg);
                }
            }
            default -> messages.send(player, "npc.usage");
        }
    }

    /** reputation <nom> [ratio] : réputation du joueur × ratio = sa réputation auprès de ce PNJ (1 par défaut). */
    private void reputation(CommandSender sender, String arg, String[] args) {
        if (arg == null || own.byId(arg).isEmpty()) {
            messages.send(sender, "npc.reputation-usage");
            return;
        }
        String name = own.byId(arg).get().name();
        if (args.length < 3) {
            messages.send(sender, "npc.reputation-ratio", "npc", name, "ratio", String.valueOf(own.ratio(name)));
            return;
        }
        double ratio;
        try {
            ratio = Double.parseDouble(args[2].replace(',', '.'));
        } catch (NumberFormatException e) {
            ratio = -1;
        }
        if (ratio < 0 || ratio > 10) {
            messages.send(sender, "npc.reputation-usage");
            return;
        }
        double value = ratio;
        own.setRatio(name, value, () -> messages.send(sender, "npc.reputation-set", "npc", name, "ratio", String.valueOf(value)));
    }

    /**
     * cleanup : emplacements des PNJ d'EterNpc qui n'existent plus ; cleanup <préfixe> confirm : tous les emplacements
     * des PNJ dont l'identifiant commence ainsi (ex : « etermarket: » après le passage à EterJob).
     */
    private void cleanup(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm") || !prefix.endsWith(":")) {
                messages.send(sender, "npc.cleanup-confirm", "prefix", prefix.endsWith(":") ? prefix : prefix + ":");
                return;
            }
            Tasks.async(plugin, () -> {
                int removed = npcs.deletePlacementsByPrefix(prefix);
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "npc.cleaned", "count", String.valueOf(removed)));
            }, "Nettoyage des emplacements " + prefix);
            return;
        }
        Set<String> existing = own.names().stream().map(name -> OwnNpc.PREFIX + name).collect(Collectors.toSet());
        Tasks.async(plugin, () -> {
            int removed = 0;
            for (Placement placement : npcs.allPlacements()) {
                if (placement.npc().startsWith(OwnNpc.PREFIX) && !existing.contains(placement.npc())) {
                    npcs.removePlacement(placement.id());
                    removed++;
                }
            }
            if (removed > 0) {
                Bukkit.getScheduler().runTask(plugin, npcs::reload);
                npcs.changed();
            }
            int count = removed;
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "npc.cleaned", "count", String.valueOf(count)));
        }, "Nettoyage des emplacements orphelins");
    }

    private Optional<Placement> nearestOwn(Player player) {
        return npcs.spawner().nearest(player.getLocation(), RADIUS, id -> id.startsWith(OwnNpc.PREFIX));
    }

    /** « guilde » -> « eternpc:guilde » (un PNJ d'EterNpc) ; « eterreward:podium_1 » reste tel quel. */
    private String full(String id) {
        return id.contains(":") ? id : OwnNpc.PREFIX + id;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(ACTIONS.stream(), args[0]);
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (action) {
                case "edit", "copy", "delete", "reputation" -> filter(own.names().stream(), args[1]);
                case "place", "tp", "move" -> filter(Stream.concat(own.names().stream(), npcs.spawner().ids().stream()
                        .filter(id -> !id.startsWith(OwnNpc.PREFIX))), args[1]);
                case "cleanup" -> filter(Stream.of("etermarket:"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (action.equals("delete") || action.equals("cleanup"))) {
            return filter(Stream.of("confirm"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> values, String start) {
        String prefix = start.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.startsWith(prefix)).toList();
    }
}
