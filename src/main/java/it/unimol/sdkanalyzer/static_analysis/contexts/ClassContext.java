package it.unimol.sdkanalyzer.static_analysis.contexts;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.annotations.Annotation;

import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class ClassContext {
    private IClass iClass;
    private JarContext context;

    private Map<IMethod, MethodContext> methodMap;

    public ClassContext(IClass iClass, JarContext context) {
        this.iClass = iClass;
        this.context = context;

        this.methodMap = new HashMap<>();
    }

    public JarContext getJarContext() {
        return context;
    }

    public MethodContext resolveMethodContext(String selector) {
        IMethod method = iClass.getMethod(Selector.make(selector));

        return this.resolveMethodContext(method);
    }

    public boolean isForcingDetectionSkip() {
        // TODO define an annotation through which developers can force the tool to skip check on a method/class
        for (Annotation annotation : iClass.getAnnotations()) {
            if (annotation.getType().getName().toString().equals("Lit/unimol/sdkanalyzer/ForceSkip")) {
                return true;
            }
        }

        return false;
    }

    public MethodContext resolveMethodContext(IMethod method) {
        if (method != null) {
            if (!methodMap.containsKey(method)) {
                MethodContext context = new MethodContext(method, this);
                this.methodMap.put(method, context);
                return context;
            } else {
                return this.methodMap.get(method);
            }
        } else
            throw new RuntimeException("No such a method");
    }

    public Collection<IMethod> getNonAbstractMethods() {
        Collection<IMethod> methods = new ArrayList<>();
        for (IMethod iMethod : this.iClass.getDeclaredMethods()) {
            if (!iMethod.isAbstract())
                methods.add(iMethod);
        }
        return methods;
    }

    public IClass getIClass() {
        return this.iClass;
    }
}
