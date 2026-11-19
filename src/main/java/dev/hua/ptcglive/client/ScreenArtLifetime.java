package dev.hua.ptcglive.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.Screen;
import java.util.IdentityHashMap;
import java.util.Map;

/** removed() also runs when opening a child. Decide after the screen switch completes. */
final class ScreenArtLifetime {
    private static final Map<Screen, Runnable> suspended = new IdentityHashMap<>();
    static {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var iterator = suspended.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (client.currentScreen == entry.getKey()) {
                    iterator.remove(); // Returned: the screen owns its cache again.
                } else if (!isChildOf(client.currentScreen, entry.getKey())) {
                    Runnable release = entry.getValue();
                    iterator.remove();
                    release.run(); // Exit, disconnect, or replacement by a different screen.
                }
            }
        });
    }

    static void removed(Screen owner, Runnable release) {
        suspended.put(owner, release);
    }

    private static boolean isChildOf(Screen current, Screen owner) {
        while (current != null) {
            if (current == owner) return true;
            if (current instanceof CardViewScreen preview) current = preview.parentScreen();
            else if (current instanceof ElectricImpactDebugScreen debug) current = debug.parentScreen();
            else if (current instanceof CardAcquisitionScreen acquisition) current = acquisition.parentScreen();
            else return false;
        }
        return false;
    }
}
