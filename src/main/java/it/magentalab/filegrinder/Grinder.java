/*
 * Copyright Magenta srl 2022
 */
package it.magentalab.filegrinder;

import java.nio.file.Path;

public interface Grinder {

    /**
     * Is this a path to be grinded?
     *
     * @param path the path
     * @return true if the path should be processed
     */
    public boolean isToBeGrinded(Path path);
    
    public void grind(Path path);

    public void complete();
}
