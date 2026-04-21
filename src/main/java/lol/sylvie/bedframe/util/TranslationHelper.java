package lol.sylvie.bedframe.util;

import xyz.nucleoid.server.translations.api.language.ServerLanguageDefinition;

import java.util.ArrayList;

public class TranslationHelper {
    public static ArrayList<String> LANGUAGES = new ArrayList<>();
    static {
        /*
        for (ServerLanguageDefinition language : ServerLanguageDefinition.getAllLanguages()) {
            String code = language.code();
            String[] sides = code.split("_");
            if (sides.length == 2) {
                LANGUAGES.add(sides[0] + "_" + sides[1].toUpperCase());
            } else LANGUAGES.add(code);
        }*/
        LANGUAGES.add("en_US");
    }
}
