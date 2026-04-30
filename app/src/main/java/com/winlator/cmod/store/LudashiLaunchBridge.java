package com.winlator.cmod.store;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;

public final class LudashiLaunchBridge {

    private LudashiLaunchBridge() {}

    public static void addToLauncher(Activity activity, String gameName, String exePath) {
        new Thread(() -> {
            Handler h = new Handler(Looper.getMainLooper());
            ContainerManager manager = new ContainerManager(activity);
            ArrayList<Container> containers = manager.getContainers();

            if (containers == null || containers.isEmpty()) {
                h.post(() -> Toast.makeText(activity,
                        "No Wine container found. Create one first.",
                        Toast.LENGTH_LONG).show());
                return;
            }

            String[] names = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) {
                String n = containers.get(i).getName();
                names[i] = (n != null && !n.isEmpty()) ? n : "Container " + i;
            }

            h.post(() -> new AlertDialog.Builder(activity)
                    .setTitle("Select container for \"" + gameName + "\"")
                    .setItems(names, (dialog, which) ->
                            writeShortcut(activity, containers.get(which), gameName, exePath, h))
                    .setNegativeButton("Cancel", null)
                    .show());
        }).start();
    }

    private static void writeShortcut(Activity activity, Container container,
                                      String gameName, String exePath, Handler h) {
        new Thread(() -> {
            try {
                File desktopDir = container.getDesktopDir();

                if (desktopDir == null) {
                    h.post(() -> Toast.makeText(activity,
                            "Container desktop directory not found.",
                            Toast.LENGTH_LONG).show());
                    return;
                }

                if (!desktopDir.exists() && !desktopDir.mkdirs()) {
                    h.post(() -> Toast.makeText(activity,
                            "Could not create desktop directory.",
                            Toast.LENGTH_LONG).show());
                    return;
                }

                String safeName = gameName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                if (safeName.isEmpty()) safeName = "game";

                File shortcutFile = new File(desktopDir, safeName + ".desktop");

                String winPath = GogInstallPath.toWinePath(activity, exePath);
                String escapedWinPath = winPath.replace("\\", "\\\\\\\\");

                String content = "[Desktop Entry]\n"
                        + "Name=" + gameName + "\n"
                        + "Exec=wine " + escapedWinPath + "\n"
                        + "Icon=\n"
                        + "Type=Application\n"
                        + "StartupWMClass=explorer\n"
                        + "\n"
                        + "[Extra Data]\n";

                try (FileWriter fw = new FileWriter(shortcutFile)) {
                    fw.write(content);
                }

                h.post(() -> Toast.makeText(activity,
                        "\"" + gameName + "\" added to Shortcuts.\n"
                                + "Open the side menu → Shortcuts to launch and configure it.",
                        Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                h.post(() -> Toast.makeText(activity,
                        "Failed to add shortcut: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
