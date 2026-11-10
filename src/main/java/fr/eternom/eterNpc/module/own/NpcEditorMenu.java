package fr.eternom.eterNpc.module.own;

import fr.eternom.eterLib.helper.gui.Frame;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterNpc.module.npc.PlacementRepository.Placement;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L'éditeur d'un PNJ d'EterNpc, 6 lignes :
 * <pre>
 *  ▣ ▣ ▣ ▣ ☺ ▣ ▣ ▣ ▣     ☺ = le PNJ (identifiant, emplacements, clics)
 *  ▣ N D S E P T G ▣     nom · ligne sous le nom · skin · équipement · pose · taille · halo
 *  ▣ ➊ ➋ ➌ ✉ ♪ ⚿ ⌛ ▣     actions clic droit · clic gauche · accroupi · dialogue · son · permission · délai
 *  ▣ ◉ · ⌖ ✎ ▶ · ✖ ▣     regard · placer ici · actions de l'emplacement le plus proche · essayer · supprimer
 *  ▣ · · · · · · · ▣
 *  ▣ ▣ ▣ ▣ « ▣ ▣ ▣ ▣
 * </pre>
 */
class NpcEditorMenu implements Menu {

    private static final int INFO = 4;
    private static final int NAME = 10;
    private static final int DESCRIPTION = 11;
    private static final int SKIN = 12;
    private static final int EQUIPMENT = 13;
    private static final int POSE = 14;
    private static final int SCALE = 15;
    private static final int GLOW = 16;
    private static final int RIGHT = 19;
    private static final int LEFT = 20;
    private static final int SHIFT = 21;
    private static final int DIALOGUE = 22;
    private static final int SOUND = 23;
    private static final int PERMISSION = 24;
    private static final int COOLDOWN = 25;
    private static final int LOOK = 28;
    private static final int PLACE = 30;
    private static final int PLACEMENT = 31;
    private static final int TEST = 32;
    private static final int DELETE = 34;
    private static final int BACK = 49;
    private static final double[] SCALES = {0.5, 0.75, 1, 1.25, 1.5, 2};
    private static final double PLACEMENT_RADIUS = 5;

    private final OwnNpcs npcs;
    private final NpcEditor editor;
    private final Messages messages;
    private final Player viewer;
    private final OwnNpc npc;
    private final Inventory inventory;

    NpcEditorMenu(OwnNpcs npcs, NpcEditor editor, Player viewer, OwnNpc npc) {
        this.npcs = npcs;
        this.editor = editor;
        this.messages = npcs.messages();
        this.viewer = viewer;
        this.npc = npc;
        this.inventory = Bukkit.createInventory(this, 54, text("editor.title", "npc", npc.name()));
        render();
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        switch (slot) {
            case NAME -> editor.askText(player, npc, "name", npc.display(), 256, OwnNpc::withDisplay);
            case DESCRIPTION -> editor.askText(player, npc, "description", npc.description(), 256, OwnNpc::withDescription);
            case SKIN -> editor.askSkin(player, npc);
            case EQUIPMENT -> equipment(player, click);
            case POSE -> save(player, npc.withPose(nextPose()));
            case SCALE -> save(player, npc.withScale(nextScale(click.isRightClick())));
            case GLOW -> save(player, npc.withGlow(!npc.glow()));
            case RIGHT -> editor.askActions(player, npc, OwnNpc.Click.RIGHT);
            case LEFT -> editor.askActions(player, npc, OwnNpc.Click.LEFT);
            case SHIFT -> editor.askActions(player, npc, OwnNpc.Click.SHIFT);
            case DIALOGUE -> editor.askDialogue(player, npc);
            case SOUND -> editor.askText(player, npc, "sound", npc.sound(), 128, OwnNpc::withSound);
            case PERMISSION -> editor.askText(player, npc, "permission", npc.permission(), 128,
                    (current, value) -> current.withPermission(value.isBlank() ? null : value));
            case COOLDOWN -> editor.askCooldown(player, npc);
            case LOOK -> save(player, click.isRightClick() ? npc.withLook(npc.look(), npc.lookRange() >= 16 ? 4 : npc.lookRange() + 4)
                    : npc.withLook(!npc.look(), npc.lookRange()));
            case PLACE -> {
                Sounds.click(player);
                npcs.npcs().place(npc.id(), player.getLocation(), () -> messages.send(player, "npc.placed", "npc", npc.id()));
            }
            case PLACEMENT -> {
                Optional<Placement> nearest = npcs.npcs().spawner().nearest(player.getLocation(), PLACEMENT_RADIUS,
                        id -> id.equals(npc.id()));
                if (nearest.isEmpty()) {
                    Sounds.refuse(player);
                    messages.send(player, "npc.none-near", "radius", String.valueOf((int) PLACEMENT_RADIUS));
                } else {
                    editor.askPlacementActions(player, npc, nearest.get());
                }
            }
            case TEST -> {
                player.closeInventory();
                fr.eternom.eterLib.EterLib.get().getActions().run(player, npc.right());
            }
            case DELETE -> editor.confirmDelete(player, npc);
            case BACK -> {
                Sounds.page(player);
                player.openInventory(new NpcListMenu(npcs, editor, player, 0).getInventory());
            }
            default -> {
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Clic avec un objet en main : il va dans la main du PNJ (une armure : à sa place) ; Maj : seconde main ; clic droit : tout retirer. */
    private void equipment(Player player, ClickType click) {
        if (click.isRightClick()) {
            OwnNpc cleared = npc;
            for (EquipmentSlot slot : npc.equipment().keySet()) {
                cleared = cleared.withEquipment(slot, null);
            }
            save(player, cleared);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            Sounds.refuse(player);
            messages.send(player, "editor.equipment.hold");
            return;
        }
        EquipmentSlot slot = click.isShiftClick() ? EquipmentSlot.OFF_HAND : held.getType().getEquipmentSlot();
        save(player, npc.withEquipment(slot, held.getType()));
    }

    private void save(Player player, OwnNpc changed) {
        Sounds.click(player);
        npcs.save(changed);
        player.openInventory(new NpcEditorMenu(npcs, editor, player, changed).getInventory());
    }

    private String nextPose() {
        // « Assis » est toujours proposé : le PNJ s'assoit sur un support invisible (NpcSpawner)
        List<Pose> poses = java.util.stream.Stream.concat(Mannequin.validPoses().stream(), java.util.stream.Stream.of(Pose.SITTING)).distinct()
                .filter(pose -> List.of(Pose.STANDING, Pose.SNEAKING, Pose.SITTING, Pose.SLEEPING, Pose.SWIMMING, Pose.FALL_FLYING).contains(pose))
                .sorted((a, b) -> a.name().compareTo(b.name())).toList();
        if (poses.isEmpty()) {
            return "STANDING";
        }
        int index = poses.stream().map(Pose::name).toList().indexOf(npc.pose());
        return poses.get((index + 1) % poses.size()).name();
    }

    private double nextScale(boolean down) {
        int index = 2;
        for (int i = 0; i < SCALES.length; i++) {
            if (Math.abs(SCALES[i] - npc.scale()) < 0.01) {
                index = i;
            }
        }
        return SCALES[Math.floorMod(index + (down ? -1 : 1), SCALES.length)];
    }

    private void render() {
        Frame.draw(inventory, Material.CYAN_STAINED_GLASS_PANE);
        inventory.setItem(INFO, Items.item(Material.ARMOR_STAND, text("editor.info.name", "npc", npc.id()), List.of(
                text("editor.info.placed", "count", String.valueOf(npcs.npcs().placedHere(npc.id()))),
                text("editor.info.clicks", "count", String.valueOf(npcs.clicksOf(npc))))));
        inventory.setItem(NAME, button(Material.NAME_TAG, "name", npcs.render(npc.display())));
        inventory.setItem(DESCRIPTION, button(Material.PAPER, "description",
                npc.description().isBlank() ? text("editor.none") : npcs.render(npc.description())));
        inventory.setItem(SKIN, button(Material.PLAYER_HEAD, "skin", Component.text(npc.textureValue() != null ? "texture"
                : npc.skin() == null ? "-" : npc.skin())));
        List<Component> equipment = new ArrayList<>();
        npc.equipment().forEach((slot, material) -> equipment.add(Component.text(slot.name() + " : ")
                .append(Component.translatable(material.translationKey()))));
        List<Component> equipmentLore = new ArrayList<>(equipment.isEmpty() ? List.of(text("editor.none")) : equipment);
        equipmentLore.add(text("editor.equipment.lore"));
        inventory.setItem(EQUIPMENT, Items.item(Material.IRON_CHESTPLATE, text("editor.equipment.name"), equipmentLore));
        inventory.setItem(POSE, button(Material.ARMOR_STAND, "pose", Component.text(npc.pose())));
        inventory.setItem(SCALE, button(Material.SLIME_BALL, "scale", Component.text("×" + npc.scale())));
        inventory.setItem(GLOW, button(npc.glow() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER, "glow", text(npc.glow() ? "editor.yes" : "editor.no")));
        inventory.setItem(RIGHT, actions(Material.LIME_DYE, "actions-right", npc.right()));
        inventory.setItem(LEFT, actions(Material.RED_DYE, "actions-left", npc.left()));
        inventory.setItem(SHIFT, actions(Material.YELLOW_DYE, "actions-shift", npc.shift()));
        Dialogue dialogue = Dialogue.parse(npc.dialogue());
        inventory.setItem(DIALOGUE, button(Material.WRITABLE_BOOK, "dialogue", text("editor.dialogue.summary",
                "lines", String.valueOf(dialogue.lines().size()), "choices", String.valueOf(dialogue.choices().size()))));
        inventory.setItem(SOUND, button(Material.NOTE_BLOCK, "sound", Component.text(npc.sound().isBlank() ? "-" : npc.sound())));
        inventory.setItem(PERMISSION, button(Material.IRON_BARS, "permission",
                Component.text(npc.permission() == null ? "-" : npc.permission())));
        inventory.setItem(COOLDOWN, button(Material.CLOCK, "cooldown", Component.text(npc.cooldown() + " s")));
        inventory.setItem(LOOK, button(npc.look() ? Material.ENDER_EYE : Material.ENDER_PEARL, "look",
                text(npc.look() ? "editor.look.on" : "editor.look.off", "range", String.valueOf((int) npc.lookRange()))));
        inventory.setItem(PLACE, button(Material.COMPASS, "place", Component.empty()));
        inventory.setItem(PLACEMENT, button(Material.FILLED_MAP, "placement", Component.empty()));
        inventory.setItem(TEST, button(Material.LEVER, "test", Component.empty()));
        inventory.setItem(DELETE, button(Material.TNT, "delete", Component.empty()));
        inventory.setItem(BACK, Items.item(Material.OAK_DOOR, text("editor.back"), List.of()));
    }

    private org.bukkit.inventory.ItemStack button(Material icon, String key, Component value) {
        List<Component> lore = new ArrayList<>();
        if (!value.equals(Component.empty())) {
            lore.add(text("editor.current").append(value));
        }
        lore.add(text("editor." + key + ".lore"));
        return Items.item(icon, text("editor." + key + ".name"), lore);
    }

    private org.bukkit.inventory.ItemStack actions(Material icon, String key, List<String> lines) {
        List<Component> lore = new ArrayList<>();
        if (lines.isEmpty()) {
            lore.add(text("editor.none"));
        }
        lines.stream().limit(8).forEach(line -> lore.add(Component.text("▸ " + line)));
        lore.add(text("editor." + key + ".lore"));
        return Items.item(icon, text("editor." + key + ".name"), lore);
    }

    private Component text(String key, String... placeholders) {
        return messages.get(viewer, key, placeholders);
    }
}
