package it.unimol.sdkanalyzer.lifetime;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Simone Scalabrino.
 */
public class APILifetimeImporter {
    private static final APILifetimeImporter instance = new APILifetimeImporter();

    public static APILifetimeImporter getInstance() {
        return instance;
    }

    private APILifetimeImporter() {
    }

    public List<APILife> load(String fileName) throws IOException {
        List<APILife> result = new ArrayList<>();
        Pattern pattern = Pattern.compile("<(.*): ([^ ]+) ([^(]+)\\(([^)]*)\\)>:\\[([0-9]+),([0-9]+)\\]");

        int lineNumber = 0;
        for (String line : Files.readAllLines(Paths.get(fileName))) {
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

            StringBuilder signatureBuilder = new StringBuilder();
            for (String parameter : methodParameters.split(",")) {
                if (parameter.length() > 0)
                    signatureBuilder.append(transformClass(parameter, false));
            }
            returnType = transformClass(returnType, true);

            String walaMethodSignature = className + "." + methodSignature + "(" + signatureBuilder.toString() + ")" + returnType;

            result.add(new APILife(walaMethodSignature, minSdk, maxSdk));
        }

        return result;
    }

    private String transformClass(String classIdentifier, boolean voidAllowed) {
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
                return arraysString+"I";
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