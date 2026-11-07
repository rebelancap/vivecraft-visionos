package org.lwjgl.sdl;

/** klsdl: windows have an empty property set (no native window handles to hand out); getters answer the default. */
public class SDLProperties {
    protected SDLProperties() { throw new UnsupportedOperationException(); }

    public static long SDL_GetPointerProperty(int props, CharSequence name, long default_value) {
        KlSdl.log("SDL_GetPointerProperty(" + props + ", " + name + "): no such property, default");
        return default_value;
    }
}
