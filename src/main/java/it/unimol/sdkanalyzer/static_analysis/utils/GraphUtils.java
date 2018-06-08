package it.unimol.sdkanalyzer.static_analysis.utils;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import it.unimol.sdkanalyzer.graphs.SubCFG;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.*;

/**
 * @author Simone Scalabrino.
 */
public class GraphUtils {
    public static BackDominators<ISSABasicBlock> buildBackDominators(SSACFG cfg) {
        SubCFG realCFG = new SubCFG(cfg, false);

        return new BackDominators<>(realCFG, cfg.exit());
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    public static ISSABasicBlock getBackDominator(BackDominators<ISSABasicBlock> backDominators, ISSABasicBlock ifCondition) {
        SimpleDirectedGraph<ISSABasicBlock, DefaultEdge> backDominatorTree = backDominators.getBackDominatorTree();
        for (DefaultEdge edge : backDominatorTree.incomingEdgesOf(ifCondition)) {
            return backDominatorTree.getEdgeSource(edge);
        }

        assert false : "Cannot arrive here! Something went wrong...";
        throw new RuntimeException("Something went wrong with dominator trees...");
    }

    private static <V> DirectedGraph<V, DefaultEdge> reverseGraph(DirectedGraph<V, DefaultEdge> graph) {
        DirectedGraph<V, DefaultEdge> result = new SimpleDirectedGraph<>(DefaultEdge.class);

        for (V v : graph.vertexSet()) {
            result.addVertex(v);
        }

        for (DefaultEdge e : graph.edgeSet()) {
            result.addEdge(graph.getEdgeTarget(e), graph.getEdgeSource(e));
        }

        return result;
    }

    /**
     * Compute dominator of a graph according to:
     * "A Simple, Fast Dominance Algorithm" Cooper et. al.
     *
     * @author giograno
     *
     * @param <V>
     *            Vertex of graph
     */
    public static class BackDominators<V> {
        private DirectedGraph<V, DefaultEdge> graph;
        private Vector<V> vertexPreOrder;
        private Hashtable<V, V> idom = null;
        private Hashtable<V, Integer> preOrderMap;
        private SimpleDirectedGraph<V, DefaultEdge> dominatorTree;


        protected int getOrder(V vertex) {
            return preOrderMap.get(vertex);
        }

        protected V getIDom(V vertex) {
            if (vertex == null)
                throw new RuntimeException("Vertex cannot be null");

            if (idom == null)
                computeBackDominators();

            return idom.get(vertex);
        }

        /**
         * Constructor of BackDominators class, using default pre-order traversal
         *
         * @param graph
         *            the graph
         * @param exitPoint
         *            exitPoint point of the graph
         */
        public BackDominators(DirectedGraph<V, DefaultEdge> graph, V exitPoint) {
            this.graph = GraphUtils.reverseGraph(graph);
            this.vertexPreOrder = dfsPreOrder(this.graph, exitPoint);
            this.preOrderMap = new Hashtable<>();
            for (int i = 0; i < this.vertexPreOrder.size(); i++) {
                preOrderMap.put(vertexPreOrder.get(i), i);
            }
            // computing dominator here!
            //computeBackDominators();
            computeBackDominatorTree();
        }

        private static <V> Vector<V> dfsPreOrder(DirectedGraph<V, DefaultEdge> graph, V exit) {
            DepthFirstIterator<V, DefaultEdge> iter = new DepthFirstIterator<>(graph, exit);
            iter.setCrossComponentTraversal(false);
            Vector<V> trav = new Vector<>();
            while (iter.hasNext()) {
                trav.add(iter.next());
            }
            return trav;
        }

        protected void computeBackDominators() {
            if (this.idom != null)
                return;
            this.idom = new Hashtable<>();
            V firstElement = vertexPreOrder.firstElement();
            idom.put(firstElement, firstElement);
            if (!graph.incomingEdgesOf(vertexPreOrder.firstElement()).isEmpty())
                throw new AssertionError(
                        "The entry of the flow graph is not allowed to have incoming edges");
            boolean changed;
            do {
                changed = false;
                for (V v : vertexPreOrder) {
                    if (v.equals(firstElement))
                        continue;
                    V oldIdom = getIDom(v);
                    V newIdom = null;
                    for (DefaultEdge edge : graph.incomingEdgesOf(v)) {
                        V pre = graph.getEdgeSource(edge);
                        if (getIDom(pre) == null) /* not yet analyzed */
                            continue;
                        if (newIdom == null) {
                            /*
                             * If we only have one (defined) predecessor pre,
                             * IDom(v) = pre
                             */
                            newIdom = pre;
                        } else {
                            /*
                             * compute the intersection of all defined predecessors
                             * of v
                             */
                            newIdom = intersectIDoms(pre, newIdom);
                        }
                    }
                    if (newIdom == null)
                        throw new AssertionError("newIDom == null !, for " + v);
                    if (!newIdom.equals(oldIdom)) {
                        changed = true;
                        this.idom.put(v, newIdom);
                    }
                }
            } while (changed);
        }

        protected void computeBackDominatorTree() {
            SimpleDirectedGraph<V, DefaultEdge> domTree = new SimpleDirectedGraph<>(
                    DefaultEdge.class);
            for (V node : graph.vertexSet()) {
                domTree.addVertex(node);
                V idom = getIDom(node);
                if (idom != null && !node.equals(idom)) {
                    domTree.addVertex(idom);
                    domTree.addEdge(idom, node);
                }
            }
            this.dominatorTree = domTree;
        }

        private V intersectIDoms(V v1, V v2) {
            while (v1 != v2) {
                if (getOrder(v1) < getOrder(v2)) {
                    v2 = getIDom(v2);
                } else {
                    v1 = getIDom(v1);
                }
            }
            return v1;
        }

        /**
         * Get the table of immediate back dominators. Note that by convention, the
         * graph's entry is dominated by itself (so <code>IDOM(n)</code> is a total
         * function).</br> Note that the set <code>DOM(n)</code> is given by
         * <ul>
         * <li/><code>DOM(Entry) = Entry</code>
         * <li/><code>DOM(n) = n \cup DOM(IDOM(n))</code>
         * </ul>
         *
         * @return the table of immediate back dominators.
         */
        public Hashtable<V, V> getIDoms() {
            computeBackDominators();
            return this.idom;
        }

        /**
         * Check wheter a node back-dominates another one.
         *
         * @param dominator dominator node
         * @param dominated dominated node
         * @return true, if <code>dominator</code> backDominates <code>dominated</code>
         *         w.r.t to the entry node
         */
        public boolean backDominates(V dominator, V dominated) {
            computeBackDominators();
            if (dominator.equals(dominated))
                return true;
            V dom = getIDom(dominated);
            // as long as dominated >= dominator
            while (dom != null && getOrder(dom) >= getOrder(dominator) && !dom.equals(dominator)) {
                dom = getIDom(dom);
            }
            return dominator.equals(dom);
        }

        /**
         * Return nodes strictly back dominators of a given node
         *
         * @param node
         *            the node for which we need back dominators
         * @return a Set of V dominators nodes
         */
        public Set<V> getStrictBackDominators(V node) {
            computeBackDominators();
//		System.out.println(node);
            Set<V> strictDoms = new HashSet<>();
            V dominated = node;
            V iDom = getIDom(node);
            while (iDom != dominated) {
                strictDoms.add(iDom);
                dominated = iDom;
                iDom = getIDom(dominated);
            }
            /* my custom addition */
            strictDoms.add(node);
            /* addition of current node on dominators */
            return strictDoms;
        }

        /**
         * Returns the set of nodes that are dominated by an edge
         *
         * @param edge
         *            the edge for which look dominated nodes
         * @return a set of nodes dominated
         */
        public Set<V> getBackDominatedNodes(DefaultEdge edge) {
            computeBackDominators();
            Set<V> dominatedNodes = new HashSet<>();
            V dominator = this.graph.getEdgeTarget(edge);

//		SimpleDirectedGraph<V, DefaultEdge> dominatorTree = getBackDominatorTree();

            DepthFirstIterator<V, DefaultEdge> depthFirstIterator = new DepthFirstIterator<>(
                    this.dominatorTree, dominator);

            while (depthFirstIterator.hasNext()) {
                V currentNode = depthFirstIterator.next();
                if (currentNode != dominator)
                    dominatedNodes.add(currentNode);
            }

            return dominatedNodes;
        }

        /**
         * Get the dominator tree
         *
         * @return a dominator tree
         */
        public SimpleDirectedGraph<V, DefaultEdge> getBackDominatorTree() {
            computeBackDominators();
            computeBackDominatorTree();
            return this.dominatorTree;
        }

        public List<V> getNonBackDominators() {
            List<V> result = new ArrayList<>();

            SimpleDirectedGraph<V, DefaultEdge> dominatorTree = this.getBackDominatorTree();

            for (V testNode : dominatorTree.vertexSet()) {
                if (dominatorTree.outgoingEdgesOf(testNode).size() == 0)
                    result.add(testNode);
            }

            return result;
        }
    }
}
