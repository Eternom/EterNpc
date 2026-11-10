# EterNpc

Les PNJ du réseau pour tous les plugins Eter : des **Mannequins natifs** (pas de Citizens), leurs emplacements en base,
une API, et depuis 2.0.0 **ses propres PNJ créés en jeu**, dont les clics lancent des **actions d'EterLib**
(`eterjob:quests mineur`, `eterlib:message ...`). Un plugin n'a donc plus besoin de définir de PNJ : il déclare ses
actions, et le staff les met sur n'importe quel PNJ. Document développeur, à tenir à jour avec le code.

## Prérequis

- **EterLib 1.22.0+** (`depend`) : base, Redis et bus réseau, langues, menus, Dialogs, **actions**, vanish.
- **EterHub** (facultatif, `softdepend`) : sa présence signale un lobby (emplacements communs à tous les lobbys).
- Client 1.21.6+ pour les Dialogs de l'éditeur.

## Fonctionnement

**Apparence en mémoire, emplacements en base.** Un PNJ apparaît s'il est à la fois **défini** (par un plugin avec
`NpcApi#define`, ou par EterNpc pour les siens) et **placé** (`eternpc_placements`). Un PNJ peut être placé autant de fois
qu'on veut, sur n'importe quel serveur.

**Portée** (`config.yml > scope`) : `auto` = `@lobbies` sur un lobby (EterHub installé), sinon le nom du serveur ;
`@resource` pour tous les mondes ressource (à mettre dans leur modèle : leurs noms changent) ; ou un nom précis. Poser,
déplacer ou retirer prévient les autres serveurs (bus `eternpc`, message `reload`) ; Redis en panne : `/eternpc reload`.

**Mannequins** (`NpcSpawner`) : invulnérables, immobiles, silencieux ; tout dégât et tout feu sont annulés, et un PNJ
disparu réapparaît (vérifié toutes les 5 s). **Jamais enregistrés dans le monde** : ils apparaissent avec le chunk de
leur emplacement et disparaissent avec lui. Chaque Mannequin porte l'identifiant de son PNJ et de son emplacement.
**Allure** (`NpcStyle`) : équipement, pose (dont « assis » : le PNJ monte sur un support invisible, Minecraft ne permettant pas cette pose seule), taille, regard (activable, distance ; ignore spectateurs et joueurs en vanish),
halo, et **permission** (le PNJ n'est visible et cliquable qu'avec elle).

**Au-dessus de la tête** (`Overlays`), pour UN joueur : un **marqueur** (`NpcApi#marker`, ex : « ! » quand une quête est
prête) gardé tant qu'il est connecté, ou une **bulle** de texte quelques secondes (`NpcApi#say`). Ce sont des
TextDisplay non enregistrés, visibles seulement par ce joueur, recréés quand le Mannequin réapparaît.

## PNJ d'EterNpc (`module/own`)

Créés en jeu (`/eternpc create <nom>` : créé et placé là où l'on est, puis l'éditeur), gardés dans `eternpc_npcs`,
communs au réseau (bus `eternpc-own`). Identifiant : `eternpc:<nom>`.

**Clics** : clic droit, clic droit accroupi (vide : comme le clic droit), clic gauche (un coup). Chacun a sa **chaîne
d'actions** d'EterLib (une ligne par action, vérifiées par EterLib avant d'être enregistrées). Un **emplacement** peut
avoir ses propres actions de clic droit (le même PNJ fait autre chose au lobby). Avant les actions : permission, délai
par joueur (0,5 s au moins contre les doubles clics), petit geste du bras, son.

**Dialogue** (`Dialogue`, testé) : au clic droit, chaque ligne est dite dans le chat (nom du PNJ devant) et la première
en bulle au-dessus de lui ; les lignes `> texte | action ; action` sont des **choix cliquables**. Sans choix, les actions
du clic droit suivent.

**Éditeur** (`/eternpc edit [nom]`, sans nom : le PNJ le plus proche) : nom (MiniMessage, ou `lang:clé` pour un texte de
`lang/ > names`), ligne sous le nom, skin (pseudo ou texture), équipement (l'objet tenu en main), pose, taille, halo,
actions des trois clics, dialogue, son, permission, délai, regard, placer ici, actions de l'emplacement le plus proche,
essayer, supprimer (confirmé, avec le nombre d'emplacements). Statistiques de clics (comptées en base chaque minute).

**Exemples** : chef de guilde = `eterjob:guild` ; donneur de quêtes du mineur = `eterjob:quests mineur` ; un PNJ du lobby
qui envoie sur la survie et ouvre le menu à l'arrivée = `@survie eterjob:quests mineur` (action réseau d'EterLib).

## API (pour les autres plugins)

`fr.eternom.eterNpc.api.NpcApi` (`NpcApi.get()`), `compileOnly("com.github.Eternom:EterNpc:<tag>")` :

- `define(plugin, NpcLook)` / `undefine(id)` : apparence d'un PNJ sur ce serveur ; `style(id, NpcStyle)` : son allure ;
- `place(id, location, done)`, `removeNearest(location, rayon, quels PNJ, done)` ;
- `deletePlacements(id)`, `deletePlacementsByPrefix(préfixe)` (bloquants) ; `importPlacement(...)` ;
- `npcOf(entité)`, `placedHere(id)` ;
- `marker(joueur, id, texte)`, `say(joueur, id, texte, durée)` ;
- événement `NpcClickEvent` (joueur, identifiant, clic droit ou accroupi, emplacement) : clic droit, une fois.

Utilisé par EterReward (podium du classement général : `eterreward:podium_<1-5>`). Les PNJ de métier sont des PNJ d'EterNpc
avec les actions d'EterJob.

## Commandes et permissions

| Commande | Rôle |
|---|---|
| `/eternpc menu` | Tous les PNJ connus ici (clic : éditeur, Maj + clic : y aller) |
| `/eternpc create <nom>` · `edit [nom]` · `copy <nom> <nouveau>` · `delete <nom> confirm` | PNJ d'EterNpc |
| `/eternpc place <pnj>` · `remove` · `move [pnj]` · `tp <pnj>` | Emplacements (le plus proche) |
| `/eternpc cleanup` · `cleanup <préfixe> confirm` | Emplacements orphelins · ceux d'un ancien plugin (ex : `etermarket:`) |
| `/eternpc list` · `reload` | Liste · rechargement |

Tout demande `eternpc.admin` (op). Alias : `/npc`.

## Données

- `eternpc_placements` : `id`, `npc`, `scope`, `world`, `x`, `y`, `z`, `yaw`, `actions` (clic droit propre à l'emplacement).
- `eternpc_npcs` : `name`, `display`, `description`, `skin`, `texture_value`, `texture_signature`, `actions_right`,
  `actions_left`, `actions_shift`, `dialogue`, `equipment`, `pose`, `scale`, `look`, `look_range`, `glow`, `permission`,
  `sound`, `cooldown`, `clicks`.
