/*
 * Copyright Magenta srl 2022
 */
package it.magentalab.filegrinder.grinders;

import it.magentalab.filegrinder.Grinder;
import it.magentalab.filegrinder.State;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given some velocity templates, extract <i>parse</i> directives to generate a
 * PlantUML diagram showing include files tree.
 *
 * @author stefano
 */
public class VelocityGrinder implements Grinder {

    private Path result = Path.of("result.plantuml");

    private Set<String> filenames = new HashSet<>();
    private Map<String, Set<String>> nodeWithSons = new HashMap<>();

    private final String PARSE_REGEX = """
                                       ^\\s*#parse\\("([^"]+)"\\)""";
    private final Pattern PARSE_PATTERN = Pattern.compile(PARSE_REGEX);

    public boolean isToBeGrinded(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && path.getFileName().toString().endsWith(".vm");
    }

    @Override
    public void grind(Path path) {
        String filename = path.getFileName().toString();
        System.out.println("Grinding " + filename);

        Set<String> sons = new HashSet<>();
        try ( var reader = Files.newBufferedReader(path)) {
            reader.lines()
                    .map(l -> l.replaceAll("\\s", ""))
                    .map(l -> PARSE_PATTERN.matcher(l))
                    .filter(m -> m.matches())
                    .forEach(m -> sons.add(m.group(1)));
        } catch (IOException ex) {
            System.err.println("error grinding " + filename + ". Error: " + ex.getMessage());
        }

        if (!sons.isEmpty()) {
            filenames.add(filename);
            filenames.addAll(sons);
            nodeWithSons.put(filename, sons);
        }
    }

    private String toState(String s) {
        return s.toUpperCase().replace(".", "_");
    }

    @Override
    public void complete() {
        try ( var writer = new PrintWriter(Files.newBufferedWriter(result))) {
            writer.println("@startuml");
            writer.println("title Velocity template inclusion tree");
            writer.println("");

            for (String filename : filenames) {
                writer.println(
                        MessageFormat.format(
                                "state {0} as \"{1}\"",
                                toState(filename), filename
                        ));
            }

            writer.println("");

            for (String node : nodeWithSons.keySet()) {
                for (String son : nodeWithSons.get(node)) {
                    writer.println(
                            MessageFormat.format(
                                    "{0} ---> {1}", toState(node), toState(son)
                            ));
                }
            }

            writer.println("");
            writer.println("@enduml");
        } catch (IOException ex) {
            System.err.println(ex.toString());
        }
    }

}
