package fr.eternom.eterNpc.module.own;

import java.util.ArrayList;
import java.util.List;

/**
 * Le dialogue d'un PNJ (logique pure, testée), écrit ligne par ligne dans l'éditeur :
 * <pre>
 *     Bienvenue à la guilde, {player} !
 *     Que veux-tu faire ?
 *     > Rejoindre un métier | eterjob:guild
 *     > Mes quêtes | eterjob:quests ; eterlib:sound entity.villager.yes
 * </pre>
 * Les lignes simples sont dites dans le chat ; les lignes « > texte | actions » sont des choix cliquables, chacun lançant
 * sa chaîne d'actions (séparées par « ; »). Sans choix, le clic droit lance ensuite les actions du PNJ.
 */
public record Dialogue(List<String> lines, List<Choice> choices) {

    /** Un choix : son texte (MiniMessage) et ses actions. */
    public record Choice(String label, List<String> actions) {
    }

    public static final Dialogue NONE = new Dialogue(List.of(), List.of());

    public Dialogue {
        lines = List.copyOf(lines);
        choices = List.copyOf(choices);
    }

    public boolean isEmpty() {
        return lines.isEmpty() && choices.isEmpty();
    }

    /** Lit le texte de l'éditeur ; une ligne « > » sans « | » ou sans action est ignorée. */
    public static Dialogue parse(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        List<String> lines = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith(">")) {
                int bar = line.indexOf('|');
                if (bar < 0) {
                    continue;
                }
                String label = line.substring(1, bar).strip();
                List<String> actions = splitActions(line.substring(bar + 1));
                if (!label.isEmpty() && !actions.isEmpty()) {
                    choices.add(new Choice(label, actions));
                }
            } else {
                lines.add(line);
            }
        }
        return new Dialogue(lines, choices);
    }

    /** « a:b x ; c:d » -> deux lignes d'action. */
    public static List<String> splitActions(String text) {
        List<String> actions = new ArrayList<>();
        if (text == null) {
            return actions;
        }
        for (String part : text.split(";")) {
            if (!part.isBlank()) {
                actions.add(part.strip());
            }
        }
        return actions;
    }

    /** Toutes les actions des choix (pour les vérifier). */
    public List<String> allActions() {
        return choices.stream().flatMap(choice -> choice.actions().stream()).toList();
    }
}
