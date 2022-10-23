/*
 * Copyright Magenta srl 2021
 */
package it.magentalab.filegrinder;

import it.magentalab.filegrinder.grinders.VelocityGrinder;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.nio.file.StandardCopyOption.*;
import java.util.stream.Stream;

/**
 *
 * @author stefano
 */
public class App {

    public static boolean dryRun = true;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Please provide input folder to process");
            System.exit(1);
        }
        var dir = args[0];
        if (args.length > 1) {
            dryRun = !(args[1] != null && args[1].length() > 0);
        }

        if (dryRun) {
            System.out.println("DRY RUN");
        }

        var path = Path.of(dir);
        System.out.println("Read files in " + path.toAbsolutePath().toString());

        run(path);
    }

    private static void run(Path inputDir) {
        Grinder grinder = new VelocityGrinder();

        try ( Stream<Path> walk = Files.walk(inputDir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(p -> grinder.isToBeGrinded(p))
                    .forEach(p -> grinder.grind(p));
        } catch (Exception ex) {
            System.err.println(ex.toString());
        }
        grinder.complete();
        
        System.out.println("Done");
    }

    private static boolean isJava(Path p) {
        var isJava = Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                && p.getFileName().toString().endsWith(".java");
        return isJava;
    }

    private static void clean(Path javaFile) {
        System.out.println("Cleaning " + javaFile);
        Path newPath = javaFile.resolveSibling(javaFile.getFileName() + ".new");

        try {
            Files.deleteIfExists(newPath);
        } catch (IOException ex) {
            System.err.println(ex.toString());
            System.err.println("========== ERROR FILE " + javaFile);
            return;
        }

        var state = State.START;

        try ( var writer = Files.newBufferedWriter(newPath)) {
            for (String l : Files.readAllLines(javaFile)) {
                String toWrite = null;
                switch (state) {
                    case START:
                        toWrite = l;
                        if (l.startsWith("package")) {
                            state = State.ACTIVE;
                        }
                        break;

                    case ACTIVE:
                        if (l.contains("it.infogroup.daf.")) {
                            // skip row, it's an old import
                            toWrite = null;
                        } else {
                            toWrite = l.replaceFirst("^package", "import");
                        }

                        if (l.startsWith("public class")) {
                            state = State.ENDING;
                        }
                        break;

                    case ENDING:
                    default:
                        toWrite = l;
                        break;
                }

                if (toWrite != null) {
                    writer.write(toWrite + System.lineSeparator());
                }
            }
        } catch (IOException ex) {
            state = State.ERROR;
            System.err.println(ex.toString());
            System.err.println("========== ERROR FILE " + javaFile);
        }

        if (state != State.ERROR) {
            try {
                replace(javaFile, newPath);
            } catch (IOException ex) {
                System.err.println(ex.toString());
                System.err.println("========== ERROR FILE " + javaFile);
            }
        }
    }

    private static void replace(Path javaFile, Path newPath) throws IOException {
        if (!dryRun) {
            Files.move(javaFile, javaFile.resolveSibling(javaFile.getFileName() + ".bak"), REPLACE_EXISTING);
            Files.move(newPath, newPath.resolveSibling(javaFile.getFileName()));
        } else {
            System.out.println("DRY RUN: delete " + newPath);
            Files.delete(newPath);
        }
        System.out.println("End cleaning of " + javaFile);
    }

}
