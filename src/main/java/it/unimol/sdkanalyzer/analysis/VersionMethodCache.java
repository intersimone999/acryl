package it.unimol.sdkanalyzer.analysis;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import it.unimol.sdkanalyzer.static_analysis.contexts.ClassContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.JarContext;
import it.unimol.sdkanalyzer.static_analysis.contexts.MethodContext;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds a cache of methods that return version labels
 * @author Simone Scalabrino.
 */
public class VersionMethodCache implements IVersionMethodCache {
    private Map<String, SDKInfo> cache;
    private JarContext context;

    public VersionMethodCache(JarContext context) {
        this.cache = new HashMap<>();
        this.context = context;
    }

    public void build() throws IOException {
        VersionMethodLabeler labeler = new VersionMethodLabeler();

        for (IClass iClass : this.context.getClassesInJar(true)) {
            ClassContext classContext = this.context.resolveClassContext(iClass);
//            System.out.println("Labeling methods from " + classContext.getIClass().getName().toString());
            for (IMethod iMethod : classContext.getNonAbstractMethods()) {
                MethodContext methodContext = classContext.resolveMethodContext(iMethod);

                //Saves an empty entry to avoid infinite loops due recursion or cyclic calls
                this.saveEntry(methodContext, null);
                SDKInfo sdkInfo = labeler.labelMethod(methodContext);
                this.saveEntry(methodContext, sdkInfo);
            }
        }
    }

    public void saveEntry(MethodContext methodContext, SDKInfo sdkInfo) {
        this.cache.put(methodContext.getIMethod().getSignature(), sdkInfo);
    }

    public SDKInfo getVersionNumbers(String signature) {
        return cache.getOrDefault(signature, null);
    }

    public SDKInfo getVersionNumbers(MethodContext context) {
        String signature = context.getIMethod().getSignature();
        return getVersionNumbers(signature);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, SDKInfo> stringSDKInfoEntry : this.cache.entrySet()) {
            String key      = stringSDKInfoEntry.getKey()   == null ? "[???]"  : stringSDKInfoEntry.getKey();
            String value    = stringSDKInfoEntry.getValue() == null ? "[none]" : stringSDKInfoEntry.getValue().toString();

            result.append(key).append(" -> ").append(value).append("\n");
        }

        return result.toString();
    }
}
