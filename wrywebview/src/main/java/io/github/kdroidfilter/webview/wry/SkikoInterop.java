package io.github.kdroidfilter.webview.wry;

import org.jetbrains.skiko.HardwareLayer;

import java.awt.*;

final class SkikoInterop {
    private SkikoInterop() {}

    static Canvas createHost() {
        try {
            if (isWindows()) {
                return new Canvas();
            }
            return new HardwareLayer();
        } catch (Throwable e) {
            return new Canvas();
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("windows");
    }

    static long getContentHandle(Component component) {
        if (component instanceof HardwareLayer) {
            return ((HardwareLayer) component).getContentHandle();
        }
        return 0L;
    }

    static long getWindowHandle(Component component) {
        if (component instanceof HardwareLayer) {
            return ((HardwareLayer) component).getWindowHandle();
        }
        return 0L;
    }

    static boolean init(Component component) {
        if (component instanceof HardwareLayer) {
            ((HardwareLayer) component).init();
            return true;
        }
        return false;
    }
}
