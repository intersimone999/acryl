package it.unimol.acryl.android;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * @author Simone Scalabrino.
 */
public class Aapt {
    private final File program;

    public Aapt(String btPath) {
        this.program = new File(btPath, "aapt");
    }

    public String extractManifest(ApkContainer apkContainer) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        Process process = runtime.exec(new String[] {this.program.getAbsolutePath(), "l", "-a", apkContainer.getFile().getAbsolutePath()});
        BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

        StringBuilder manifest = new StringBuilder();
        boolean inManifest = false;

        String lineOutput;
        while ((lineOutput = stdInput.readLine()) != null) {
            if (inManifest) {
                manifest.append(lineOutput).append("\n");
            }

            if (lineOutput.startsWith("Android manifest")) {
                inManifest = true;
            }
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Aborted process");
        }

        return manifest.toString();
    }
}
