package fr.eternom.eterNpc.api;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * L'allure d'un PNJ, en plus de son apparence (NpcLook) : équipement, pose, taille, regard, halo, visibilité.
 * Depuis EterNpc 2.0.0. {@link #DEFAULT} : debout, taille normale, regarde le joueur le plus proche à 8 blocs.
 *
 * @param equipment  objet de chaque emplacement (main, tête...), vide : rien
 * @param pose       pose du Mannequin (STANDING, SNEAKING, SITTING... ; une pose refusée par le jeu reste debout)
 * @param scale      taille (1 = normale, de 0,25 à 3)
 * @param look       tourne la tête vers le joueur le plus proche
 * @param lookRange  distance du regard, en blocs
 * @param glow       halo (contour lumineux) visible de tous
 * @param permission visible et cliquable seulement avec cette permission (null : par tous)
 */
public record NpcStyle(Map<EquipmentSlot, Material> equipment, String pose, double scale, boolean look, double lookRange,
                       boolean glow, String permission) {

    public static final NpcStyle DEFAULT = new NpcStyle(Map.of(), "STANDING", 1, true, 8, false, null);

    public NpcStyle {
        equipment = Map.copyOf(equipment);
        scale = Math.clamp(scale, 0.25, 3);
        lookRange = Math.clamp(lookRange, 1, 32);
    }
}
