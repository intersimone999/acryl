package it.unimol.sdkanalyzer.rules.detectors.forward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class ForwardCompatibilityBadSmellDetector extends SingleRuleViolationDetector {
    private static final String MESSAGE = "You should use this APIs differently from SDK version %d. Add a check and handle with: %s";
    private final APILifetime apiLifetime;

    public ForwardCompatibilityBadSmellDetector(APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) throws IOException {
        return false;
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    ) {
        return null;
    }
}
