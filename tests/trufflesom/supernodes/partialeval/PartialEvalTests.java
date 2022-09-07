package trufflesom.supernodes.partialeval;

import bdt.basic.ProgramDefinitionError;
import bdt.source.SourceCoordinate;
import bdt.tools.structure.StructuralProbe;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import trufflesom.compiler.*;
import trufflesom.interpreter.Method;
import trufflesom.interpreter.SomLanguage;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.LocalVariableNodeFactory;
import trufflesom.interpreter.nodes.SequenceNode;
import trufflesom.interpreter.objectstorage.StorageAnalyzer;
import trufflesom.interpreter.supernodes.LocalVariableReadSquareWriteNodeGen;
import trufflesom.interpreter.supernodes.LocalVariableSquareNode;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SSymbol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertThat;
import static trufflesom.vm.SymbolTable.symSelf;
import static trufflesom.vm.SymbolTable.symbolFor;

public class PartialEvalTests extends PartialEvaluationTest {
    private static final boolean NO_SUPERNODES = true;
    private static final boolean SUPERNODES_ON = false;

    protected ClassGenerationContext cgenc;
    protected MethodGenerationContext mgenc;

    static {
        initTruffle();
    }

//    private static class TestSupernodeRootNode extends RootNode {
//        @Child private LocalVariableSquareNode superNode;
//        @Child private SequenceNode seqSuperNode;
//
//        TestSupernodeRootNode(LocalVariableSquareNode superNode) {
//            super(null);
//            this.superNode = superNode;
//        }
//
//        TestSupernodeRootNode(SequenceNode seqSuperNode) {
//            super(null);
//            this.seqSuperNode = seqSuperNode;
//            this.getFrameDescriptor().addFrameSlot(1, FrameSlotKind.Long);
//            this.getFrameDescriptor().addFrameSlot(2, FrameSlotKind.Long);
//            this.getFrameDescriptor().addFrameSlot(5, FrameSlotKind.Long);
//            this.getFrameDescriptor().addFrameSlot(6, FrameSlotKind.Long);
//            this.getFrameDescriptor().addFrameSlot(7, FrameSlotKind.Long);
////            this.getFrameDescriptor().addFrameSlot(1);
//        }
//
//        @Override
//        public Object execute(VirtualFrame frame) {
////            return this.superNode.executeGeneric(frame);
//            frame.setLong(1, 10);
////            frame.getArguments()d
//            return this.seqSuperNode.executeGeneric(frame);
//        }
//    }

    private static void initTruffle() {
        StorageAnalyzer.initAccessors();

        Context.Builder builder = Universe.createContextBuilder();
        builder.logHandler(System.err);

        Context context = builder.build();
        context.eval(SomLanguage.INIT);

        Universe.selfSource = SomLanguage.getSyntheticSource("self", "self");
        Universe.selfCoord = SourceCoordinate.createEmpty();
    }

    protected SInvokable parseMethodInvokable(final String source, boolean noSupernodes) {
        Source s = SomLanguage.getSyntheticSource(source, "test");

        cgenc = new ClassGenerationContext(s, null);
        cgenc.setName(symbolFor("Test"));
//        addAllFields();

        mgenc = new MethodGenerationContext(cgenc, (StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable>) null);
        mgenc.addArgumentIfAbsent(symSelf, 0);

        ParserAst parser = new ParserAst(source, s, null);
        parser.setNoSupernodes(noSupernodes);

        try {
            ExpressionNode parsedMethod = parser.method(mgenc);
            return mgenc.assemble(parsedMethod, 0);
//            return mgenc.assemble(((SequenceNode) parsedMethod).expressions[2], 0);

        } catch (ProgramDefinitionError e) {
            throw new RuntimeException(e);
        }
    }

    protected ExpressionNode parseMethodExpression(final String source, boolean noSupernodes) {
        Source s = SomLanguage.getSyntheticSource(source, "test");

        cgenc = new ClassGenerationContext(s, null);
        cgenc.setName(symbolFor("Test"));
//        addAllFields();

        mgenc = new MethodGenerationContext(cgenc, (StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable>) null);
        mgenc.addArgumentIfAbsent(symSelf, 0);

        ParserAst parser = new ParserAst(source, s, null);
        parser.setNoSupernodes(noSupernodes);
        try {
            return parser.method(mgenc);
        } catch (ProgramDefinitionError e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T readSequenceExpressions(final Object obj, final String fieldName, final Class<T> c) {
        java.lang.reflect.Field field = lookupClassField(obj.getClass(), fieldName);
        field.setAccessible(true);
        try {
            return c.cast(field.get(obj));
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field lookupClassField(final Class<?> cls, final String fieldName) {
        try {
            return cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) {
                return lookupClassField(cls.getSuperclass(), fieldName);
            }
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Didn't find field: " + fieldName);
    }

    boolean compareStructuredGraphs(StructuredGraph graph1, StructuredGraph graph2) {
        if (graph1.getNodeCount() != graph2.getNodeCount())
            return false;

        Iterator<Node> graph1Iterator = graph1.getNodes().iterator();
        Iterator<Node> graph2Iterator = graph2.getNodes().iterator();

        while (graph1Iterator.hasNext()) {
            Node n1 = graph1Iterator.next();
            Node n2 = graph2Iterator.next();

//            System.out.println(n1 + "," + n2 + "===> " + n1.valueEquals(n2)); // Works for most but fails for FrameState, HotSpotOptimizedCallTarget, FrameDescriptor...

            if (!n1.getClass().equals(n2.getClass()))
                return false;
        }
        return true;
    }

    @Test
    public void testLoopConditionProfile() {
        // Must not compile immediately, the profile is not initialized until the first execution.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());
        this.getContext().eval(SomLanguage.INIT);

        // can be uncommented to deactivate execution before compilation, but graphs will just be a transferToInterpreter()
//        this.preventProfileCalls = true;

//        String squareCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100.0 atRandom. l3 := 0.01. l3 := l2 * l2. ^ l3 )";
        String squareCodeStr = "test = ( | l1 l2 l3 l4 | " +
                "l1 := 100.0 atRandom. l2 := 0.01. l2 := l1 * l1. " +
                "l3 := 100 atRandom. l4 := 0. l4 := l3 * l3." +
                " ^ l2 + l4)";

//        String squareCodeStr = "test = ( | l1 l2 l3 l4 | l3 := l3 * l3. ^ l3 )";
//        String intIncrementLocalCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100 atRandom. l2 := l2 + 42. ^ l3 )";
//        String intIncrementCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100 atRandom. l3 := l3 + l2. ^ l3 )";

        String codeStr = squareCodeStr;
       
//        ResolvedJavaMethod method = getResolvedJavaMethod(LocalVariableNodeFactory.LocalVariableReadNodeGen.class, "executeGeneric");
//        System.out.println(method);

//        String codeStr = "test: arg = ( | l3 | l3 := arg * arg. ^ l3 )";
//        SInvokable sInvokableSn = parseMethodInvokable(codeStr, SUPERNODES_ON);
        SInvokable sInvokableOg = parseMethodInvokable(codeStr, NO_SUPERNODES);
//        SequenceNode seqSn = (SequenceNode) parseMethodExpression(codeStr, SUPERNODES_ON);
        SequenceNode seqOg = (SequenceNode) parseMethodExpression(codeStr, NO_SUPERNODES);


//        ExpressionNode supernodeExpr = readSequenceExpressions(seqSn, "expressions", ExpressionNode[].class)[0];
//        assertThat(supernodeExpr, instanceOf(LocalVariableSquareNode.class));

//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode((LocalVariableSquareNode) supernodeExpr);
//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode(seq);
// Need something that can be called, so something that inherits from RootNode
//        OptimizedCallTarget target = (OptimizedCallTarget) testSupernodeRootNode.getCallTarget();

//        StructuredGraph graphOg = partialEval((OptimizedCallTarget) sInvokableOg.getCallTarget(),
//                new HashMap<>(Map.of("dumpGraph", "sureWhyNot", "graphDescription", "original_graph")));

//        StructuredGraph graphSn = partialEval((OptimizedCallTarget) sInvokableSn.getCallTarget(),
//                new HashMap<>(Map.of("dumpGraph", "yeahIAgree", "graphDescription", "supernode_graph")));

//        Object xdd = new BytecodeParser();
//        System.out.println("GRAPH SIMILARITY: " + compareStructuredGraphs(compile((OptimizedCallTarget) sInvokableSn.getCallTarget()), graphSn));
//        System.out.println(graphSn + "\n" + graphOg);

//        ControlFlowGraph cfg = ControlFlowGraph.compute(graphSn, true, false, false, false);
        // System.out.println(Arrays.toString(cfg.getBlocks()));

//        System.out.println("GRAPH SIMILARITY: " + compareStructuredGraphs(graphOg, graphSn));

//        System.out.println(summoningSupernodeGraphThroughFuckery());
    }

    // source is pretty much CachingPEGraphDecoder
  /*   public Object summoningSupernodeGraphThroughFuckery() {
        StructuredGraph graphToEncode = null;// @formatter:off
//        Method method = null;
//        try {
//            method = LocalVariableReadSquareWriteNodeGen.class.getMethod("executeGeneric", VirtualFrame.class);
//        } catch (NoSuchMethodException noSuchMethodException) {
//            System.out.println("method not found");
//        }

//         ResolvedJavaMethod method = null;
         Object method = null; // on veut une resolvedjavamethod pas une Method qui est une classe de l'interpret'
//         String codeStr = "test = ( | l1 l2 l3 l4 | l2 := 100.0 atRandom. l3 := 0.01. l3 := l2 * l2. ^ l3 )";
//         SInvokable sInvokableSn = parseMethodInvokable(codeStr, SUPERNODES_ON);
//         OptimizedCallTarget callTarget = (OptimizedCallTarget) sInvokableSn.getCallTarget();


        System.out.println(method);

//         MethodCallTargetNode methodCallTargetNode = null;
//         Object method = methodCallTargetNode.targetMethod();
//        methodCallTargetNode.targetMethod();

        EconomicMap<OptionKey<?>, Object> optionsMap = EconomicMap.create();
        optionsMap.put(DebugOptions.Dump, ":3");
        optionsMap.put(DebugOptions.PrintGraphHost, "localhost");
        optionsMap.put(DebugOptions.PrintGraphPort, 4445);
        optionsMap.put(DebugOptions.DescriptionStr, "supernodeExecuteGeneric");
        OptionValues options = new OptionValues(optionsMap);

        DebugContext debug = new DebugContext.Builder(new OptionValues(EconomicMap.create())).build();

        // not the exact same config as PE does
        GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(new GraphBuilderConfiguration.Plugins(new InvocationPlugins()));

//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode(null);
//        OptimizedCallTarget target = (OptimizedCallTarget) testSupernodeRootNode.getCallTarget();

//        graphToEncode = new StructuredGraph.Builder(options, debug, StructuredGraph.AllowAssumptions.YES).
//                profileProvider(null).
//                trackNodeSourcePosition(graphBuilderConfig.trackNodeSourcePosition()).
//                method(method).
//                setIsSubstitution(false).
////                cancellable(graph.getCancellable()).
//                build();

////        try (DebugContext.Scope scope = debug.scope("buildGraph", graphToEncode); DebugCloseable a = BuildGraphTimer.start(debug)) {
        IntrinsicContext initialIntrinsicContext = null;
        Providers providers = null; // might need to initialize that to a better value
        GraphBuilderPhase.Instance graphBuilderPhaseInstance = new GraphBuilderPhase.Instance(providers, graphBuilderConfig,
                OptimisticOptimizations.ALL,
                initialIntrinsicContext);
        graphBuilderPhaseInstance.apply(graphToEncode);
//        }
        return graphToEncode;
    }*/
}