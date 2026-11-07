package org.lwjgl.sdl;

import java.nio.IntBuffer;

/** klsdl: no dialogs on the headset. The box is logged and answered with its default (Return) button, else the first. */
public class SDLMessageBox {
    protected SDLMessageBox() { throw new UnsupportedOperationException(); }

    private static final int BUTTON_RETURNKEY_DEFAULT = 0x1;

    public static boolean SDL_ShowMessageBox(SDL_MessageBoxData data, IntBuffer buttonid) {
        StringBuilder b = new StringBuilder();
        int answer = -1;
        SDL_MessageBoxButtonData.Buffer bs = data.numbuttons() > 0 ? data.buttons() : null;
        for (int i = 0; bs != null && i < bs.remaining(); i++) {
            SDL_MessageBoxButtonData x = bs.get(i);
            b.append(i == 0 ? " buttons: " : ", ").append(x.textString());
            if (answer == -1 && (x.flags() & BUTTON_RETURNKEY_DEFAULT) != 0) answer = x.buttonID();
        }
        if (answer == -1 && bs != null && bs.remaining() > 0) answer = bs.get(0).buttonID();
        System.err.println("[klsdl] message box \"" + data.titleString() + "\": " + data.messageString() + b + " -> answered " + answer);
        if (buttonid != null) buttonid.put(buttonid.position(), answer);
        return true;
    }
    public static boolean SDL_ShowSimpleMessageBox(int flags, CharSequence title, CharSequence message, long window) {
        System.err.println("[klsdl] message box \"" + title + "\": " + message);
        return true;
    }
}
