package it.unimol.sdkanalyzer.graphs;

import org.jgrapht.Graph;
import org.jgrapht.ext.ComponentAttributeProvider;
import org.jgrapht.ext.ComponentNameProvider;
import org.jgrapht.ext.DOTExporter;
import org.jgrapht.graph.DefaultEdge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Simone Scalabrino.
 */
public class GraphTools {
    private static final GraphTools instance = new GraphTools();
    private static String pathToDot = "/usr/bin/dot";

    public static void setPathToDot(String pathToDot) {
        GraphTools.pathToDot = pathToDot;
    }

    public static GraphTools getInstance() {
        return GraphTools.instance;
    }

    public void dot2pdf(File dotFile, File pdfFile) throws IOException {
        Runtime runtime = Runtime.getRuntime();

        Process process = runtime.exec(new String[] {pathToDot, "-Tpdf", dotFile.getAbsolutePath(), "-o", pdfFile.getAbsolutePath()});

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Logger.getAnonymousLogger().warning("dot2pdf interrupted");
        }
    }

    @SuppressWarnings("unchecked")
    public void graph2dot(Graph graph, File destination,
                          ComponentNameProvider idProvider,
                          ComponentNameProvider vertexLabelProvider,
                          ComponentNameProvider edgeLabelProvider,
                          ComponentAttributeProvider vertexAttributeProvider,
                          ComponentAttributeProvider edgeAttributeProvider,
                          ComponentNameProvider graphIdProvider
                          ) throws IOException {
        DOTExporter exporter = new DOTExporter(
                idProvider,
                vertexLabelProvider,
                edgeLabelProvider,
                vertexAttributeProvider,
                edgeAttributeProvider,
                graphIdProvider
        );
        try (FileWriter writer = new FileWriter(destination)) {
            exporter.exportGraph(graph, writer);
        }
    }
}
