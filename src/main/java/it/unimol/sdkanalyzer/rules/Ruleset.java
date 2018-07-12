package it.unimol.sdkanalyzer.rules;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Set of detection rules
 * @author Simone Scalabrino.
 */
public class Ruleset {
    private final HashMap<String, Set<Rule>> hashMap;
    private final Set<Rule> rules;

    public Ruleset(File file, int minApps, double minConfidence) throws IOException {
        this();
        this.load(file, minApps, minConfidence);
        Logger.getAnonymousLogger().info("Using " + this.rules.size() + " rules.");
    }

    public Ruleset() {
        this.hashMap = new HashMap<>();
        this.rules   = new HashSet<>();
    }

    public Collection<Rule> matchingRules(Collection<String> apis) {
        if (apis.size() == 0)
            return new ArrayList<>();

        Set<Rule> matchingRules = new HashSet<>(this.rules);

        //Initial selection of potentially matching rules
        for (String api : apis) {
            if (hashMap.containsKey(api)) {
                matchingRules.retainAll(hashMap.get(api));
            }
        }

        Set<Rule> result = new HashSet<>();
        for (Rule matchingRule : matchingRules) {
            if (apis.containsAll(matchingRule.getTrueApis()))
                result.add(matchingRule);

            if (apis.containsAll(matchingRule.getFalseApis()))
                result.add(matchingRule);
        }

        return result;
    }

    public void addRule(Rule rule) {
        this.rules.add(rule);

        for (String apiSignature : rule.getInvolvedApis()) {
            if (!hashMap.containsKey(apiSignature)) {
                hashMap.put(apiSignature, new HashSet<>());
            }

            hashMap.get(apiSignature).add(rule);
        }
    }

    private void load(File file, int minConfidenceLevel, double minConfidence) throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(file.getAbsolutePath()))) {
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());

            for (CSVRecord csvRecord : csvParser) {
                String comparator   = csvRecord.get("comparison");
                int version         = Integer.parseInt(csvRecord.get("version"));

                String trueApis     = csvRecord.get("true_apis");
                String falseApis    = csvRecord.get("false_apis");

//                double occurrences  = Double.parseDouble(csvRecord.get("occurrences"));
                int confidenceLevel            = Integer.parseInt(csvRecord.get("napps"));
//                double confidence  = Double.parseDouble(csvRecord.get("reliability"));

                if (confidenceLevel < minConfidenceLevel)
                    continue;

                VersionChecker versionChecker = new VersionChecker();
                versionChecker.setComparator(comparator);
                versionChecker.setCheckedVersion(version);

                Collection<String> trueApisSeq  = new HashSet<>(Arrays.asList(trueApis.split("&")));
                Collection<String> falseApisSeq = new HashSet<>(Arrays.asList(falseApis.split("&")));

                if (trueApisSeq.size() == 1)
                    trueApisSeq.remove("");

                if (falseApisSeq.size() == 1)
                    falseApisSeq.remove("");

                Rule rule = new Rule(versionChecker, trueApisSeq, falseApisSeq);
                rule.setConfidence(confidenceLevel);

                this.addRule(rule);
            }

            csvParser.close();
        }
    }
}
