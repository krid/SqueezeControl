/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 */

package com.squeezecontrol.io;

import java.util.Arrays;

public class CommandParser {
    public static SqueezeCommand parse(String commandString) {
        if (commandString == null) return null;
        String parts[] = commandString.split(" ");
        if (parts.length < 3) return null;
        // <Player> <Command> <Param1> <Param2>
        String[] params = (String[]) Arrays.asList(parts).subList(2, parts.length).toArray(new String[parts.length - 2]);
        return new SqueezeCommand(SqueezeCommand.decode(parts[0]), parts[1], params);
    }
}
