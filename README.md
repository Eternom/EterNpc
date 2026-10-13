# EterNpc

Les PNJ du réseau pour tous les plugins Eter : des **Mannequins natifs** (pas de Citizens), leurs emplacements en base,
et une API. Chaque plugin garde ses propres données (rôle, boutique, classement...) et ne fait que donner l'apparence de
ses PNJ et répondre à leurs clics. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.14.0+** (`depend`) : base (dont `insertReturningId`), Redis et bus réseau, langues.
- **EterHub** (facultatif, `softdepend`) : sa présence signale un lobby (emplacements communs à tous les lobbys).

## Fonctionnement

**Apparence en mémoire, emplacements en base.** Un plugin définit ses PNJ à chaque démarrage (`NpcApi#define` :
identifiant `plugin:nom`, nom, ligne sous le nom, skin par pseudo ou texture précise) ; rien de cela n'est enregistré
ici, et les PNJ d'un plugin disparaissent quand il s'arrête. Les **emplacements** (`eternpc_placements`) sont gardés par
EterNpc : un PNJ peut être placé autant de fois qu'on veut, sur n'importe quel serveur. Un PNJ n'apparaît que s'il est
à la fois défini et placé.

**Portée** : un emplacement vaut pour le serveur où il a été posé (son `server-name`), ou, **sur un lobby** (EterHub
installé), pour `@lobbies` : tous les lobbys, même ceux créés plus tard par l'orchestrateur (nom neuf). Poser ou retirer
prévient les autres serveurs (bus `eternpc`, message `reload`, ignoré par l'émetteur) ; Redis en panne : `/eternpc reload`.

**Mannequins** (`NpcSpawner`) : invulnérables, immobiles, silencieux ; tout dégât et tout feu sont annulés (même en
créatif), et un PNJ disparu quand même réapparaît (vérifié toutes les 5 s). Tête et corps tournés vers le joueur le
plus proche (8 blocs, tous les 2 ticks), sans bouger ; sinon, la direction de l'emplacement. **Jamais enregistrés dans
le monde** (non persistants) : ils apparaissent au chargement du chunk de leur emplacement et disparaissent avec lui,
donc jamais de doublon après un redémarrage. Une apparence redéfinie à l'identique ne recharge pas le skin. Chaque
Mannequin porte l'identifiant de son PNJ (données persistantes de l'entité) pour reconnaître un clic.

## API (pour les autres plugins)

`fr.eternom.eterNpc.api.NpcApi` (`NpcApi.get()`), `compileOnly("com.github.Eternom:EterNpc:<tag>")` :

- `define(plugin, NpcLook)` / `undefine(id)` : apparence d'un PNJ sur ce serveur (thread principal) ;
- `place(id, location, done)`, `removeNearest(location, rayon, quels PNJ, done)` : poser ou retirer ici (thread principal) ;
- `deletePlacements(id)` : tous ses emplacements du réseau ; `importPlacement(...)` : reprise de données (bloquants) ;
- `npcOf(entité)`, `placedHere(id)` ;
- événement `NpcClickEvent` (joueur, identifiant) : clic droit, une fois (main principale).

Utilisé par EterMarket (boutiques, métiers, hôtel des ventes : `etermarket:<pnj>`) et EterReward (podium du classement
général : `eterreward:podium_<1-5>`).

## Commandes et permissions

| Commande | Permission | Par défaut |
|---|---|---|
| `/eternpc list` · `place <pnj>` · `remove` (le plus proche, 5 blocs) · `reload` (`npc`) | `eternpc.admin` | op |

Les plugins gardent leurs propres commandes (ex : `/market place`, `/eterreward podium`).

## Données

- `eternpc_placements` : `id`, `npc`, `scope` (nom du serveur ou `@lobbies`), `world`, `x`, `y`, `z`, `yaw`.
