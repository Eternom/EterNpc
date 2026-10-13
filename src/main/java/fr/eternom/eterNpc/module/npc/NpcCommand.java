package fr.eternom.eterNpc.module.npc;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /eternpc (staff) : list (PNJ définis ici et leurs emplacements) · place <pnj> (ici, dans ta direction) · remove (le
 * plus proche) · reload. Les plugins gardent leurs propres commandes (ex : /market place).
 */
public class NpcCommand implements TabExecutor {

    private static final List<String> ACTIONS = List.of("list", "place", "remove", "reload");
    private static final double REMOVE_RADIUS = 5;

    private final NpcService npcs;
    private final Messages messages;

    public NpcCommand(NpcService npcs, Messages messages) {
        this.npcs = npcs;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "list" -> {
                List<String> ids = npcs.spawner().ids();
                messages.send(sender, ids.isEmpty() ? "npc.list-empty" : "npc.list", "npcs", String.join(", ", ids.stream()
                        .map(id -> id + " (" + npcs.placedHere(id) + ")").toList()));
            }
            case "reload" -> {
                npcs.reload();
                messages.send(sender, "npc.reloaded");
            }
            case "place" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "command.players-only");
                } else if (args.length < 2 || !npcs.spawner().isDefined(args[1].toLowerCase(Locale.ROOT))) {
                    messages.send(player, "npc.unknown", "npc", args.length < 2 ? "?" : args[1]);
                } else {
                    String id = args[1].toLowerCase(Locale.ROOT);
                    npcs.place(id, player.getLocation(), () -> messages.send(player, "npc.placed", "npc", id));
                }
            }
            case "remove" -> {
                if (sender instanceof Player player) {
                    npcs.removeNearest(player.getLocation(), REMOVE_RADIUS, npc -> true, removed -> removed.ifPresentOrElse(
                            id -> messages.send(player, "npc.removed", "npc", id),
                            () -> messages.send(player, "npc.none-near", "radius", String.valueOf((int) REMOVE_RADIUS))));
                } else {
                    messages.send(sender, "command.players-only");
                }
            }
            default -> messages.send(sender, "npc.usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(ACTIONS.stream(), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filter(npcs.spawner().ids().stream(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> values, String start) {
        String prefix = start.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.startsWith(prefix)).toList();
    }
}
