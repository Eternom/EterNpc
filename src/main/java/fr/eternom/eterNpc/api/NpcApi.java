package fr.eternom.eterNpc.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Les PNJ du réseau (Mannequins natifs). EterNpc les fait apparaître, tourner la tête et réapparaître, et enregistre
 * OÙ ils sont placés. Le plugin qui possède un PNJ garde ses données et donne son apparence avec {@link #define} à chaque
 * démarrage ; un clic arrive par {@link NpcClickEvent}. EterNpc a aussi ses propres PNJ (« eternpc:... »), créés en jeu,
 * dont les clics lancent des actions d'EterLib (« eterjob:quests mineur »...) : la plupart des plugins n'ont plus besoin
 * de définir de PNJ, seulement de déclarer leurs actions.
 * Un emplacement posé sur un lobby (EterHub) est commun à tous les lobbys ; sur un monde ressource, à tous les mondes
 * ressource (config scope : @resource).
 */
public interface NpcApi {

    static Optional<NpcApi> get() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(NpcApi.class));
    }

    /**
     * Thread principal. Définit ou met à jour un PNJ sur ce serveur : il apparaît là où il est placé, et ses Mannequins
     * déjà là changent d'apparence sans réapparaître. Rien n'est enregistré : à redonner à chaque démarrage. Les PNJ
     * d'un plugin disparaissent quand il s'arrête.
     */
    void define(Plugin owner, NpcLook look);

    /** Thread principal. Équipement, pose, taille, regard, halo, visibilité d'un PNJ défini (depuis 2.0.0). */
    void style(String id, NpcStyle style);

    /** Thread principal. Le PNJ disparaît de ce serveur (ses emplacements restent enregistrés). */
    void undefine(String id);

    /** Thread principal. Place le PNJ là où est location (direction comprise) ; done ensuite, sur le thread principal. */
    void place(String id, Location location, Runnable done);

    /**
     * Thread principal. Retire l'emplacement le plus proche (rayon en blocs) parmi les PNJ acceptés par which (ex : ceux
     * d'un plugin) ; done reçoit le PNJ retiré, ou rien.
     */
    void removeNearest(Location location, double radius, Predicate<String> which, Consumer<Optional<String>> done);

    /** Comme ci-dessus, parmi tous les PNJ (EterNpc 1.0.0). */
    default void removeNearest(Location location, double radius, Consumer<Optional<String>> done) {
        removeNearest(location, radius, npc -> true, done);
    }

    /** Bloquant. Supprime tous les emplacements de ce PNJ, sur tout le réseau. */
    void deletePlacements(String id);

    /** Bloquant. Supprime les emplacements de tous les PNJ dont l'identifiant commence ainsi (ex : « etermarket: »). */
    int deletePlacementsByPrefix(String prefix);

    /**
     * Bloquant. Ajoute un emplacement tel quel (reprise des données d'un plugin). scope : nom du serveur, ou
     * {@link #LOBBIES} pour tous les lobbys.
     */
    void importPlacement(String id, String scope, String world, double x, double y, double z, float yaw);

    /** Le PNJ représenté par cette entité, s'il en est un. */
    Optional<String> npcOf(Entity entity);

    /** Nombre d'emplacements de ce PNJ sur ce serveur. */
    int placedHere(String id);

    /**
     * Thread principal. Un marqueur au-dessus de la tête du PNJ, vu par ce joueur seulement (ex : « ! » quand une quête
     * est prête) ; null le retire. Gardé tant que le joueur est connecté ici. Depuis 2.0.0.
     */
    void marker(Player viewer, String id, Component marker);

    /** Thread principal. Une bulle de texte au-dessus du PNJ, vue par ce joueur, pendant duration. Depuis 2.0.0. */
    void say(Player viewer, String id, Component text, Duration duration);

    /** Portée des emplacements posés sur un lobby : communs à tous les lobbys. */
    String LOBBIES = "@lobbies";

    /** Portée des emplacements posés sur un monde ressource : communs à tous (config scope : @resource). */
    String RESOURCE = "@resource";
}
