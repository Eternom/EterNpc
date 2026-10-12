package fr.eternom.eterNpc.api;

import net.kyori.adventure.text.Component;

/**
 * L'apparence d'un PNJ, donnée par le plugin qui le possède.
 *
 * @param id          identifiant unique sur le réseau, « plugin:nom » en minuscules (ex : etermarket:forgeron)
 * @param name        nom au-dessus de la tête
 * @param description ligne sous le nom (null : aucune)
 * @param skin        pseudo dont il prend le skin, récupéré par le jeu (null : texture, ou skin par défaut)
 * @param texture     texture précise (ex : MineSkin), prioritaire sur skin ; null sinon
 */
public record NpcLook(String id, Component name, Component description, String skin, Texture texture) {

    /** Valeur et signature d'une texture de skin. */
    public record Texture(String value, String signature) {
    }
}
