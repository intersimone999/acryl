package it.unimol.sdkanalyzer.lifetime;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Simone Scalabrino.
 */
public class APILifetime {
    private Map<String, APILife> lifeMap;

    public APILifetime(Map<String, APILife> lifeMap) {
        this.lifeMap = lifeMap;
    }

    public APILife getLifeFor(String signature) {
        if (this.lifeMap.containsKey(signature))
            return this.lifeMap.get(signature);
        else
            return new APILife(signature, 0, -1);
    }

    public int size() {
        return this.lifeMap.size();
    }

    @SuppressWarnings("RegExpRedundantEscape")
    public static APILifetime load(File file) throws IOException {
        Map<String, APILife> result = new HashMap<>();
        Pattern pattern = Pattern.compile("<(.*): ([^ ]+) ([^(]+)\\(([^)]*)\\)>:\\[([0-9]+),([0-9]+)\\]");

        Map<String, List<String>> spurious = new HashMap<>();

        int lineNumber = 0;
        for (String line : FileUtils.readLines(file, "UTF-8")) {
            lineNumber++;

            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                throw new RuntimeException("Invalid line " + lineNumber + " (" + line + ")");
            }

            String className = matcher.group(1);

            String methodSignature = matcher.group(3);
            String methodParameters = matcher.group(4);
            String returnType = matcher.group(2);

            int minSdk = Integer.parseInt(matcher.group(5));
            int maxSdk = Integer.parseInt(matcher.group(6));
            if (maxSdk == 25)
                maxSdk = -1;

            assert maxSdk != 0;

            StringBuilder signatureBuilder = new StringBuilder();
            for (String parameter : methodParameters.split(",")) {
                if (parameter.length() > 0)
                    signatureBuilder.append(transformClass(parameter, false));
            }
            returnType = transformClass(returnType, true);

            String walaMethodSignature = className + "." + methodSignature + "(" + signatureBuilder.toString() + ")" + returnType;

            if (result.containsKey(walaMethodSignature)) {
                if (!spurious.containsKey(walaMethodSignature))
                    spurious.put(walaMethodSignature, new ArrayList<>());

                spurious.get(walaMethodSignature).add(line);
            }
//            assert !result.containsKey(walaMethodSignature);
            result.put(walaMethodSignature, new APILife(walaMethodSignature, minSdk, maxSdk));
        }

        for (String walaMethodToRemove : spurious.keySet()) {
            result.remove(walaMethodToRemove);
            Logger.getAnonymousLogger().info("Removing spurious signature: " + walaMethodToRemove + " for " + spurious.get(walaMethodToRemove));
        }

        return new APILifetime(result);
    }

    private static String transformClass(String classIdentifier, boolean voidAllowed) {
        int arrays = 0;
        while (classIdentifier.contains("[]")) {
            classIdentifier = classIdentifier.replace("[]", "");
            arrays++;
        }

        String arraysString = StringUtils.repeat('[', arrays);

        switch (classIdentifier) {
            case "int":
                return arraysString+"I";
            case "long":
                return arraysString+"J";
            case "boolean":
                return arraysString+"Z";
            case "double":
                return arraysString+"D";
            case "float":
                return arraysString+"F";
            case "byte":
                return arraysString+"B";
            case "char":
                return arraysString+"C";
            case "short":
                return arraysString+"S";
            case "void":
                assert voidAllowed;
                assert arrays == 0;

                return "V";
        }

        if (!classIdentifier.contains(".")) {
            return "Ljava/lang/Object;";
        }

        String result = classIdentifier;
        result = result.replace(".", "/");
        result = "L" + result + ";";

        return arraysString + result;
    }
}