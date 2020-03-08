package it.unimol.acryl.analysis;

import it.unimol.acryl.static_analysis.contexts.ClassContext;
import it.unimol.acryl.static_analysis.contexts.GlobalContext;
import it.unimol.acryl.static_analysis.contexts.JarContext;
import it.unimol.acryl.static_analysis.contexts.MethodContext;

import java.io.File;

/**
 * @author Simone Scalabrino.
 */
public class Playground {
    private static final String JAR = "classes-dex2jar.jar";
    private static final File[] CLASSPATH = new File[] {
            new File("/opt/android-sdk/platforms/android-22/android.jar"),
            new File("/opt/android-sdk/platforms/android-22/uiautomator.jar")
//            new File("jar2")
    };

    private static final String CLASS = "com.example.simone.sdktestapp1.Utils";
    private static final String METHOD = "testSDK()V";

    public static void main(String[] args) throws Exception {
        JarContext ctx = GlobalContext.getAndroidContext(JAR, CLASSPATH);

        ClassContext classCtx = ctx.resolveClassContext(CLASS);
        ClassContext classCtx2 = ctx.resolveClassContext("com.example.simone.sdktestapp1.MainActivity");

        MethodContext methodCtx0 = classCtx2.resolveMethodContext("testSDK()V");
        MethodContext methodCtx1 = classCtx.resolveMethodContext("getSDKVersion()I");
        MethodContext methodCtx2 = classCtx.resolveMethodContext("isMarshmellow()Z");
        MethodContext methodCtx3 = classCtx.resolveMethodContext("s1()I");
        MethodContext methodCtx4 = classCtx.resolveMethodContext("s2()I");
        MethodContext methodCtx5 = classCtx.resolveMethodContext("isNotMarshmellow()Z");

        VersionMethodLabeler labeler = new VersionMethodLabeler();
        SDKInfo i0 = labeler.labelMethod(methodCtx0);
        SDKInfo i1 = labeler.labelMethod(methodCtx1);
        SDKInfo i2 = labeler.labelMethod(methodCtx2);
        SDKInfo i3 = labeler.labelMethod(methodCtx3); //NOT WORKING (Won't fix)
        SDKInfo i4 = labeler.labelMethod(methodCtx4); //NOT WORKING (Won't fix)
        SDKInfo i5 = labeler.labelMethod(methodCtx5); //NOT WORKING (Won't fix)

        System.out.println("OK");

//        CFGVisitor visitor = new CFGVisitor(methodCtx1);

//        visitor.visit(block -> {
//            if (block.getLastInstruction() instanceof SSAConditionalBranchInstruction) {
//                visitor.visitConditionalBranchingBlock(block, trueSubCFG -> {
//                    System.out.println("TRUE");
//                    System.out.println(trueSubCFG);
//                }, falseSubCFG -> {
//                    System.out.println("FALSE");
//                    System.out.println(falseSubCFG);
//                });
//            }
//        });

//        AugmentedSymbolTable ast = new AugmentedSymbolTable();
//        ast.build(methodCtx5);
//        ast.build(methodCtx0);
//        ast.build(methodCtx1);
//        ast.build(methodCtx2);
//        ast.build(methodCtx3);
//        ast.build(methodCtx4);

//        System.out.println("Selected " + methodCtx.getIMethod().getSignature());

//        for (SSAInstruction ssaInstruction : methodCtx.getIntermediateRepresentation().getInstructions()) {
//            if (ssaInstruction instanceof SSAReturnInstruction) {
//                int result = ((SSAReturnInstruction) ssaInstruction).getResult();
//
//                Value value = methodCtx.getIntermediateRepresentation().getSymbolTable().getValue(result);
//                if (value instanceof ConstantValue) {
//                } else if (value instanceof PhiValue) {
//                    PhiValue phi = (PhiValue) value;
//                    phi.getPhiInstruction().getDef();
//                }
//            }
//        }
    }
}
