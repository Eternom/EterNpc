package fr.eternom.eterNpc.module.own;

import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.gui.Dialogs;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Les saisies de l'éditeur de PNJ (Dialogs) : nom, ligne sous le nom, skin, actions (une par ligne, vérifiées par
 * EterLib avant d'être enregistrées), dialogue, son, permission, délai. Chaque saisie rouvre l'éditeur ensuite.
 */
final class NpcEditor {

    private final OwnNpcs npcs;
    private final Messages messages;

    NpcEditor(OwnNpcs npcs) {
        this.npcs = npcs;
        this.messages = npcs.messages();
    }

    void open(Player admin, String name) {
        npcs.get(name).ifPresentOrElse(npc -> admin.openInventory(new NpcEditorMenu(npcs, this, admin, npc).getInventory()),
                () -> messages.send(admin, "npc.unknown", "npc", name));
    }

    /** Un champ de texte d'une ligne. */
    void askText(Player admin, OwnNpc npc, String key, String initial, int max, BiFunction<OwnNpc, String, OwnNpc> apply) {
        ask(admin, npc, key, List.of(DialogInput.text("value", messages.get(admin, "editor." + key + ".label"))
                .initial(initial == null ? "" : initial).maxLength(max).build()), response -> {
            String value = response.getText("value");
            npcs.save(apply.apply(npc, value == null ? "" : value.strip()));
        });
    }

    /** Le skin : pseudo d'un joueur, ou texture précise (valeur + signature, ex : MineSkin), prioritaire. */
    void askSkin(Player admin, OwnNpc npc) {
        ask(admin, npc, "skin", List.of(
                DialogInput.text("skin", messages.get(admin, "editor.skin.player")).initial(npc.skin() == null ? "" : npc.skin()).maxLength(16).build(),
                DialogInput.text("value", messages.get(admin, "editor.skin.value")).maxLength(4096).build(),
                DialogInput.text("signature", messages.get(admin, "editor.skin.signature")).maxLength(4096).build()), response -> {
            String skin = blank(response.getText("skin"));
            String value = blank(response.getText("value"));
            String signature = blank(response.getText("signature"));
            // Texture laissée vide : on garde l'ancienne seulement si aucun pseudo n'est donné
            npcs.save(value != null ? npc.withSkin(skin, value, signature)
                    : skin != null ? npc.withSkin(skin, null, null) : npc.withSkin(null, npc.textureValue(), npc.textureSignature()));
        });
    }

    /** Les actions d'un clic, une par ligne ; refusées (avec les erreurs) si EterLib ne les reconnaît pas. */
    void askActions(Player admin, OwnNpc npc, OwnNpc.Click click) {
        multiline(admin, npc, "actions-" + click.key(), String.join("\n", npc.actions(click) == npc.right() && click == OwnNpc.Click.SHIFT
                ? List.of() : npc.actions(click)), text -> {
            List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            if (check(admin, lines)) {
                npcs.save(npc.withActions(click, lines));
            }
        });
    }

    void askDialogue(Player admin, OwnNpc npc) {
        multiline(admin, npc, "dialogue", npc.dialogue(), text -> {
            Dialogue dialogue = Dialogue.parse(text);
            if (check(admin, dialogue.allActions())) {
                npcs.save(npc.withDialogue(text.strip()));
            }
        });
    }

    /** Les actions propres à un emplacement (vide : celles du PNJ). */
    void askPlacementActions(Player admin, OwnNpc npc, fr.eternom.eterNpc.module.npc.PlacementRepository.Placement placement) {
        multiline(admin, npc, "actions-placement", placement.actions() == null ? "" : placement.actions(), text -> {
            List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            if (check(admin, lines)) {
                npcs.npcs().setPlacementActions(placement, lines.isEmpty() ? null : String.join("\n", lines),
                        () -> messages.send(admin, "editor.saved"));
            }
        });
    }

    void askCooldown(Player admin, OwnNpc npc) {
        askText(admin, npc, "cooldown", String.valueOf(npc.cooldown()), 6, (current, value) -> {
            try {
                return current.withCooldown(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                messages.send(admin, "editor.cooldown.invalid");
                return current;
            }
        });
    }

    /** Suppression : confirmée, avec le nombre d'emplacements qui partent avec le PNJ. */
    void confirmDelete(Player admin, OwnNpc npc) {
        Tasks.async(npcs.plugin(), admin, () -> npcs.npcs().placementsOnNetwork(npc.id()).size(), count -> {
            admin.closeInventory();
            DialogBase base = DialogBase.builder(messages.get(admin, "editor.delete.title", "npc", npc.name()))
                    .body(List.of(DialogBody.plainMessage(messages.get(admin, "editor.delete.body", "npc", npc.name(),
                            "count", String.valueOf(count)))))
                    .build();
            Dialogs.show(npcs.plugin(), admin, base, messages.get(admin, "editor.delete.confirm"), messages.get(admin, "dialog.cancel"),
                    response -> npcs.delete(npc.name(), () -> messages.send(admin, "npc.deleted", "npc", npc.name())),
                    () -> open(admin, npc.name()));
        }, () -> messages.send(admin, "error.generic"));
    }

    // ---------- Outils ----------

    private void multiline(Player admin, OwnNpc npc, String key, String initial, Consumer<String> then) {
        ask(admin, npc, key, List.of(DialogInput.text("value", messages.get(admin, "editor." + key + ".label"))
                .initial(initial == null ? "" : initial).maxLength(4000)
                .multiline(TextDialogInput.MultilineOptions.create(12, 160)).width(300).build()), response -> {
            String value = response.getText("value");
            then.accept(value == null ? "" : value);
        });
    }

    private void ask(Player admin, OwnNpc npc, String key, List<? extends DialogInput> inputs, Consumer<DialogResponseView> onSave) {
        admin.closeInventory();
        DialogBase base = DialogBase.builder(messages.get(admin, "editor." + key + ".title", "npc", npc.name()))
                .body(List.of(DialogBody.plainMessage(messages.get(admin, "editor." + key + ".body"))))
                .inputs(List.copyOf(inputs))
                .build();
        Dialogs.show(npcs.plugin(), admin, base, messages.get(admin, "editor.save"), messages.get(admin, "dialog.cancel"), response -> {
            onSave.accept(response);
            Bukkit.getScheduler().runTaskLater(npcs.plugin(), () -> open(admin, npc.name()), 2);
        }, () -> open(admin, npc.name()));
    }

    /** Vérifie les actions avec EterLib : false (et les erreurs au staff) si l'une n'est pas reconnue. */
    private boolean check(Player admin, List<String> lines) {
        List<String> errors = EterLib.get().getActions().validate(admin, lines);
        errors.forEach(error -> messages.send(admin, "editor.action-error", "problem", error));
        if (!errors.isEmpty()) {
            messages.send(admin, "editor.not-saved");
        }
        return errors.isEmpty();
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
