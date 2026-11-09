package fr.eternom.eterNpc.module.own;

import fr.eternom.eterNpc.api.NpcStyle;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un PNJ d'EterNpc (créé en jeu, « eternpc:<nom> ») : son apparence, son allure, et ce que font ses clics (chaînes
 * d'actions d'EterLib), son dialogue, son son, sa permission et son délai entre deux clics.
 *
 * @param name        nom au-dessus de la tête (MiniMessage)
 * @param description ligne sous le nom (MiniMessage), vide : aucune
 * @param right       actions du clic droit (une par ligne)
 * @param left        actions du clic gauche
 * @param shift       actions du clic droit accroupi (vide : comme le clic droit)
 * @param dialogue    texte du dialogue (Dialogue), vide : aucun
 * @param sound       son joué au clic (clé Minecraft), vide : aucun
 * @param cooldown    secondes entre deux clics d'un même joueur (0 : 0,5 s contre les doubles clics)
 */
public record OwnNpc(String name, String display, String description, String skin, String textureValue, String textureSignature,
                     List<String> right, List<String> left, List<String> shift, String dialogue, Map<EquipmentSlot, Material> equipment,
                     String pose, double scale, boolean look, double lookRange, boolean glow, String permission, String sound,
                     int cooldown, long clicks) {

    public static final String PREFIX = "eternpc:";

    public OwnNpc {
        right = List.copyOf(right);
        left = List.copyOf(left);
        shift = List.copyOf(shift);
        equipment = equipment.isEmpty() ? Map.of() : Map.copyOf(equipment);
    }

    /** Un nouveau PNJ : le nom est son identifiant, skin par défaut, aucune action. */
    public static OwnNpc create(String name) {
        return new OwnNpc(name, "<accent>" + name, "", null, null, null, List.of(), List.of(), List.of(), "", Map.of(),
                "STANDING", 1, true, 8, false, null, "", 0, 0);
    }

    /** Identifiant sur le réseau : « eternpc:<nom> ». */
    public String id() {
        return PREFIX + name;
    }

    public NpcStyle style() {
        return new NpcStyle(equipment, pose, scale, look, lookRange, glow, permission);
    }

    /** Une copie sous un autre nom (/eternpc copy), sans les statistiques. */
    public OwnNpc copy(String newName) {
        return new OwnNpc(newName, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue,
                equipment, pose, scale, look, lookRange, glow, permission, sound, cooldown, 0);
    }

    // ---------- Changements (éditeur) ----------

    public OwnNpc withDisplay(String value) {
        return new OwnNpc(name, value, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withDescription(String value) {
        return new OwnNpc(name, display, value, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withSkin(String value, String texture, String signature) {
        return new OwnNpc(name, display, description, value, texture, signature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withActions(Click click, List<String> actions) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature,
                click == Click.RIGHT ? actions : right, click == Click.LEFT ? actions : left, click == Click.SHIFT ? actions : shift,
                dialogue, equipment, pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withDialogue(String value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, value, equipment,
                pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withEquipment(EquipmentSlot slot, Material material) {
        Map<EquipmentSlot, Material> next = new EnumMap<>(EquipmentSlot.class);
        next.putAll(equipment);
        if (material == null) {
            next.remove(slot);
        } else {
            next.put(slot, material);
        }
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, next,
                pose, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withPose(String value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                value, scale, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withScale(double value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, value, look, lookRange, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withLook(boolean value, double range) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, value, range, glow, permission, sound, cooldown, clicks);
    }

    public OwnNpc withGlow(boolean value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, value, permission, sound, cooldown, clicks);
    }

    public OwnNpc withPermission(String value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, value, sound, cooldown, clicks);
    }

    public OwnNpc withSound(String value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, permission, value, cooldown, clicks);
    }

    public OwnNpc withCooldown(int value) {
        return new OwnNpc(name, display, description, skin, textureValue, textureSignature, right, left, shift, dialogue, equipment,
                pose, scale, look, lookRange, glow, permission, sound, Math.max(0, value), clicks);
    }

    /** Quel clic. */
    public enum Click {
        RIGHT, LEFT, SHIFT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public List<String> actions(Click click) {
        return switch (click) {
            case RIGHT -> right;
            case LEFT -> left;
            case SHIFT -> shift.isEmpty() ? right : shift;
        };
    }

    // ---------- En base ----------

    static String equipmentText(Map<EquipmentSlot, Material> equipment) {
        StringBuilder text = new StringBuilder();
        equipment.forEach((slot, material) -> text.append(text.isEmpty() ? "" : ";").append(slot.name()).append('=').append(material.name()));
        return text.toString();
    }

    static Map<EquipmentSlot, Material> parseEquipment(String text) {
        Map<EquipmentSlot, Material> equipment = new EnumMap<>(EquipmentSlot.class);
        if (text == null || text.isBlank()) {
            return equipment;
        }
        for (String part : text.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length < 2) {
                continue;
            }
            try {
                Material material = Material.matchMaterial(pair[1]);
                if (material != null) {
                    equipment.put(EquipmentSlot.valueOf(pair[0]), material);
                }
            } catch (IllegalArgumentException ignored) {
                // emplacement inconnu : ignoré
            }
        }
        return equipment;
    }
}
