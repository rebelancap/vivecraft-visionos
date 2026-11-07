package org.lwjgl.sdl;

/** klsdl: "visionOS" inside the Klepton app (KLGLFW_HOST/KLSDL_HOST=klepton), else "macOS". */
public class SDLPlatform {
    protected SDLPlatform() { throw new UnsupportedOperationException(); }

    public static String SDL_GetPlatform() {
        String h = System.getenv("KLSDL_HOST");
        if (h == null) h = System.getenv("KLGLFW_HOST");
        return "klepton".equals(h) ? "visionOS" : "macOS";
    }
}
