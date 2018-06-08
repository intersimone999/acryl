package it.unimol.sdkanalyzer.rules.detectors.forward;

import it.unimol.sdkanalyzer.analysis.VersionChecker;
import it.unimol.sdkanalyzer.android.ApkContainer;
import it.unimol.sdkanalyzer.lifetime.APILife;
import it.unimol.sdkanalyzer.lifetime.APILifetime;
import it.unimol.sdkanalyzer.rules.Rule;
import it.unimol.sdkanalyzer.rules.CombinedViolationDetector;
import it.unimol.sdkanalyzer.rules.detectors.SingleRuleViolationDetector;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class ForwardCompatibilityBadSmellDetector extends SingleRuleViolationDetector {
    private final JarContext context;

    public ForwardCompatibilityBadSmellDetector(JarContext context) {
        this.context = context;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        if (rule.getTrueApis().size() == 0)
            return false;

        if (!apisInCode.containsAll(rule.getTrueApis()))
            return false;

        if (!codeCheck.isNull())
            return false;

        for (String api : rule.getTrueApis()) {
            MethodContext apiMethodContext = this.context.resolveMethodContext(api);
            if (apiMethodContext.isDeprecated())
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
        StringBuilder messageBuilder = new StringBuilder("[Warning] ");
        if (alternativeApis.containsAll(usedApis)) {
            // Wrong Contextual Usage
            messageBuilder.append("Some of the APIs you are using are deprecated and they should be used in a different way in newer Android releases(")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). ")
                    .append("For new versions, consider using these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        } else if (alternativeApis.size() == 0) {
            // Case of Version-Specific Control
            messageBuilder.append("Some of the APIs you are using are deprecated they should be used only in older Android versions (")
                    .append(checkToImplement.toString())
                    .append("). ")
                    .append("Add a check before using them.");
        } else {
            messageBuilder.append("You should use this APIs differently in newer Android versions (")
                    .append(checkToImplement.getInverse(true).toString())
                    .append("). For new versions, consider using these APIs: ")
                    .append(alternativeApisString)
                    .append(".");
        }

        return new CombinedViolationDetector.RuleViolationReport(
                CombinedViolationDetector.RuleViolation.ForwardBadSmell,
                messageBuilder.toString(),
                rule.getConfidence(),
                usedApis
        );
    }
}
