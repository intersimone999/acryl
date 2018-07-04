package it.unimol.sdkanalyzer.rules.detectors.forward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILife;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class ForwardCompatibilityBugDetector extends PotentialForwardCompatibilityDetector {
    private static final String MESSAGE = "You must use this APIs differently from SDK version %d. Add a check and handle with: %s";
    private final int apiLevel;
    private final APILifetime apiLifetime;

    public ForwardCompatibilityBugDetector(int apiLevel, APILifetime apiLifetime) {
        this.apiLifetime = apiLifetime;
        this.apiLevel = apiLevel;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        if (!super.violatesRule(apk, methodContext, codeCheck, rule, apisInCode))
            return false;

        for (String api : rule.getTrueApis()) {
            APILife apiLife = this.apiLifetime.getLifeFor(api);

            if (apiLife.getMaxVersion() < apiLevel && apiLife.getMaxVersion() != -1)
                return true;
        }

        return false;
    }

    public CombinedViolationDetector.RuleViolationReport buildReport(
            ApkContainer apk,
            Rule rule,
            VersionChecker checkToImplement,
            VersionChecker actualCheck,
            Collection<String> usedApis,
            Collection<String> alternativeApis
    ) {
        String alternativeApisString = StringUtils.join(alternativeApis, " --- ");
        StringBuilder messageBuilder = new StringBuilder("[Critical] ");
        if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage

            messageBuilder.append("The APIs you are using must be used in a different way in newer Android releases(")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). ")
                    .append("For new versions, use these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("The APIs you are using must be used only in old Android versions (")
                    .append(checkToImplement.toString())
                    .append("). ")
                    .append("Add a check.");
        } else {
            messageBuilder.append("You must use this APIs differently in newer Android versions (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). For new versions, use these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        }
        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.ForwardCriticalBug,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis
        );
    }
}
