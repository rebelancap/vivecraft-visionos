package org.lwjgl.sdl;

import java.util.concurrent.ConcurrentHashMap;

/** klsdl: hints are remembered (SDL_GetHint answers them) and otherwise have no effect. */
public class SDLHints {
    protected SDLHints() { throw new UnsupportedOperationException(); }

    private static final ConcurrentHashMap<String, String> HINTS = new ConcurrentHashMap<>();

    public static boolean SDL_SetHint(CharSequence name, CharSequence value) {
        if (name == null) return false;
        if (value == null) HINTS.remove(name.toString()); else HINTS.put(name.toString(), value.toString());
        return true;
    }
    public static String SDL_GetHint(CharSequence name) { return name == null ? null : HINTS.get(name.toString()); }
}
