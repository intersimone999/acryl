package it.unimol.sdkanalyzer.rules.detectors;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public abstract class SingleRuleViolationDetector {
    public abstract boolean violatesRule(ApkContainer apk, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException;

    /**
     * Builds a report given the specified parameters
     * @param rule violated rule
     * @param checkToImplement check in the rule that should be implemented
     * @param actualCheck check actually implemented in code
     * @param usedApis APIs used in code
     * @param alternativeApis APIs suggested in alternative version of checkToImplement
     * @return
     */
    public abstract CombinedViolationDetector.RuleViolationReport buildReport(
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    );
}
