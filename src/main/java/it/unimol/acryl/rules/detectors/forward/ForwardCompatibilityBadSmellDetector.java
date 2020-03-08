package it.unimol.acryl.rules.detectors.forward;

import it.unimol.acryl.analysis.VersionChecker;
import it.unimol.acryl.android.ApkContainer;
import it.unimol.acryl.rules.CombinedViolationDetector;
import it.unimol.acryl.rules.Rule;
import it.unimol.acryl.static_analysis.contexts.JarContext;
import it.unimol.acryl.static_analysis.contexts.MethodContext;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;

/**
 * @author Simone Scalabrino.
 */
public class ForwardCompatibilityBadSmellDetector extends PotentialForwardCompatibilityDetector {
    private final JarContext context;

    public ForwardCompatibilityBadSmellDetector(JarContext context) {
        this.context = context;
    }

    @Override
    public boolean violatesRule(ApkContainer apk, MethodContext methodContext, VersionChecker codeCheck, Rule rule, Collection<String> apisInCode) {
        if (!super.violatesRule(apk, methodContext, codeCheck, rule, apisInCode))
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
                usedApis,
                alternativeApis,
                rule.getChecker().toString()
        );
    }
}
