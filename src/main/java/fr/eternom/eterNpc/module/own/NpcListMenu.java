package fr.eternom.eterNpc.module.own;

import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Tous les PNJ connus ici (/eternpc menu), 6 lignes, 28 par page : ceux d'EterNpc (clic : éditeur) et ceux des autres
 * plugins (lecture seule). Maj + clic : se téléporter à son emplacement le plus proche sur ce serveur.
 */
class NpcListMenu implements Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    private static final int PREVIOUS = 45;
    private static final int NEXT = 53;
    private static final int BACK = 49;
    private final BackButton back = EterLib.get().backButton("close");

    private final OwnNpcs npcs;
    private final NpcEditor editor;
    private final Messages messages;
    private final Player viewer;
    private final List<String> ids;
    private final int page;
    private final Inventory inventory;

    NpcListMenu(OwnNpcs npcs, NpcEditor editor, Player viewer, int page) {
        this.npcs = npcs;
        this.editor = editor;
        this.messages = npcs.messages();
        this.viewer = viewer;
        List<String> all = new ArrayList<>(npcs.names().stream().map(name -> OwnNpc.PREFIX + name).toList());
        npcs.npcs().spawner().ids().stream().filter(id -> !all.contains(id)).forEach(all::add);
        this.ids = all;
        int pages = Math.max(1, (all.size() + SLOTS.length - 1) / SLOTS.length);
        this.page = Math.clamp(page, 0, pages - 1);
        this.inventory = Bukkit.createInventory(this, 54, messages.get(viewer, "list-menu.title", "count", String.valueOf(all.size())));
        render(pages);
        inventory.setItem(BACK, back.item(viewer));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            back.click(player);
            return;
        }
        if (slot == PREVIOUS || slot == NEXT) {
            Sounds.page(player);
            player.openInventory(new NpcListMenu(npcs, editor, player, page + (slot == NEXT ? 1 : -1)).getInventory());
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            int index = page * SLOTS.length + i;
            if (SLOTS[i] != slot || index >= ids.size()) {
                continue;
            }
            String id = ids.get(index);
            if (click.isShiftClick()) {
                if (!npcs.teleport(player, id)) {
                    Sounds.refuse(player);
                }
            } else if (id.startsWith(OwnNpc.PREFIX)) {
                Sounds.click(player);
                editor.open(player, id.substring(OwnNpc.PREFIX.length()));
            } else {
                Sounds.refuse(player);
                messages.send(player, "list-menu.not-own", "npc", id);
            }
            return;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render(int pages) {
        Frame.draw(inventory, Material.CYAN_STAINED_GLASS_PANE);
        for (int i = 0; i < SLOTS.length; i++) {
            int index = page * SLOTS.length + i;
            if (index >= ids.size()) {
                break;
            }
            String id = ids.get(index);
            boolean own = id.startsWith(OwnNpc.PREFIX);
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get(viewer, "list-menu.placed", "count", String.valueOf(npcs.npcs().placedHere(id))));
            if (own) {
                npcs.byId(id).ifPresent(npc -> lore.add(messages.get(viewer, "list-menu.clicks", "count", String.valueOf(npcs.clicksOf(npc)))));
                lore.add(messages.get(viewer, "list-menu.edit"));
            } else {
                lore.add(messages.get(viewer, "list-menu.owner", "plugin", npcs.npcs().owner(id).orElse("?")));
            }
            lore.add(messages.get(viewer, "list-menu.teleport"));
            Component name = npcs.npcs().spawner().look(id).map(look -> look.name()).orElse(Component.text(id));
            inventory.setItem(SLOTS[i], Items.item(own ? Material.PLAYER_HEAD : Material.SKELETON_SKULL, name, lore));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS, Items.item(Material.ARROW, messages.get(viewer, "list-menu.previous"), List.of()));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT, Items.item(Material.ARROW, messages.get(viewer, "list-menu.next"), List.of()));
        }
    }

}
