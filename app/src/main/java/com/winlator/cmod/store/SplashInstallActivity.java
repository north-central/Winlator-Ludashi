package com.winlator.cmod.store;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.OnExtractFileListener;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.ImageFs;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class SplashInstallActivity extends Activity {

    private static final int LATEST_VERSION = 21;

    private ProgressBar progressBar;
    private TextView statusText;
    private TextView percentText;
    private Button proceedButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isAlreadyInstalled()) {
            launchMainActivity();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_splash_install);

        ImageView logo = findViewById(R.id.splash_logo);
        if (logo != null) logo.setImageResource(R.mipmap.ic_launcher_foreground);

        statusText    = findViewById(R.id.splash_status_text);
        progressBar   = findViewById(R.id.splash_progress_bar);
        percentText   = findViewById(R.id.splash_percent_text);
        proceedButton = findViewById(R.id.splash_proceed_button);

        if (proceedButton != null) {
            proceedButton.setVisibility(View.GONE);
            proceedButton.setOnClickListener(v -> onProceedClicked());
        }

        Executors.newSingleThreadExecutor().execute(this::runInstall);
    }

    private boolean isAlreadyInstalled() {
        ImageFs imageFs = ImageFs.find(this);
        return imageFs.isValid() && imageFs.getVersion() >= LATEST_VERSION;
    }

    private void runInstall() {
        try {
            ImageFs imageFs = ImageFs.find(this);
            File rootDir = imageFs.getRootDir();

            clearRootDir(rootDir);
            setProgress("Installing system files", 0);

            long rawSize = FileUtils.getSize(this, "imagefs.txz");
            final long contentLength = (long) (rawSize * (100.0f / 22));
            AtomicLong totalSizeRef = new AtomicLong();

            OnExtractFileListener progressListener = (file, size) -> {
                if (size > 0) {
                    long total = totalSizeRef.addAndGet(size);
                    int pct = (int) Math.min(((float) total / contentLength) * 100, 99);
                    handler.post(() -> setProgress("Installing system files", pct));
                }
                return file;
            };

            boolean success = TarCompressorUtils.extract(
                    TarCompressorUtils.Type.XZ, this, "imagefs.txz", rootDir, progressListener);

            if (!success) {
                handler.post(() -> Toast.makeText(this,
                        "Installation failed — please reinstall the app.", Toast.LENGTH_LONG).show());
                return;
            }

            handler.post(() -> setProgress("Extracting Wine", 99));
            String[] wineVersions = getResources().getStringArray(R.array.wine_entries);
            for (String ver : wineVersions) {
                File outFile = new File(rootDir, "/opt/" + ver);
                outFile.mkdirs();
                TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, this, ver + ".txz", outFile);
            }

            handler.post(() -> setProgress("Installing drivers", 99));
            AdrenotoolsManager adrenoMgr = new AdrenotoolsManager(this);
            String[] drivers = getResources().getStringArray(
                    R.array.wrapper_graphics_driver_version_entries);
            for (String drv : drivers) adrenoMgr.extractDriverFromResources(drv);

            imageFs.createImgVersionFile(LATEST_VERSION);

            File libDir = imageFs.getLibDir();
            FileUtils.symlink("libSDL2-2.0.so",
                    new File(libDir, "libSDL2-2.0.so.0").getAbsolutePath());

            handler.post(() -> {
                setProgress("Installation complete", 100);
                if (proceedButton != null) proceedButton.setVisibility(View.VISIBLE);
            });

        } catch (Exception e) {
            handler.post(() -> {
                Toast.makeText(this, "Setup error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                launchMainActivity();
            });
        }
    }

    private void setProgress(String message, int pct) {
        if (statusText  != null) statusText.setText(message);
        if (progressBar != null) progressBar.setProgress(pct);
        if (percentText != null) percentText.setText(pct + "%");
    }

    private void onProceedClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
        launchMainActivity();
    }

    private void launchMainActivity() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && "home".equals(f.getName())) continue;
                    deleteRecursive(f);
                }
            }
        } else {
            rootDir.mkdirs();
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
