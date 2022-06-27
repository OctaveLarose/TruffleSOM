package trufflesom.supernodes.partialeval;

import bd.basic.ProgramDefinitionError;
import bd.source.SourceCoordinate;
import bd.tools.structure.StructuralProbe;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import trufflesom.compiler.*;
import trufflesom.interpreter.SomLanguage;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.SequenceNode;
import trufflesom.interpreter.objectstorage.StorageAnalyzer;
import trufflesom.interpreter.supernodes.LocalVariableSquareNode;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SSymbol;

import java.util.Arrays;
import java.util.List;

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

    private static class TestSupernodeRootNode extends RootNode {
        @Child private LocalVariableSquareNode superNode;
        @Child private SequenceNode seqSuperNode;

        TestSupernodeRootNode(LocalVariableSquareNode superNode) {
            super(null);
            this.superNode = superNode;
        }

        TestSupernodeRootNode(SequenceNode seqSuperNode) {
            super(null);
            this.seqSuperNode = seqSuperNode;
            this.getFrameDescriptor().addFrameSlot(1, FrameSlotKind.Long);
            this.getFrameDescriptor().addFrameSlot(2, FrameSlotKind.Long);
            this.getFrameDescriptor().addFrameSlot(5, FrameSlotKind.Long);
            this.getFrameDescriptor().addFrameSlot(6, FrameSlotKind.Long);
            this.getFrameDescriptor().addFrameSlot(7, FrameSlotKind.Long);
//            this.getFrameDescriptor().addFrameSlot(1);
        }

        @Override
        public Object execute(VirtualFrame frame) {
//            return this.superNode.executeGeneric(frame);
            frame.setLong(1, 10);
//            frame.getArguments()d
            return this.seqSuperNode.executeGeneric(frame);
        }
    }

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
            return mgenc.assemble(parser.method(mgenc), 0);
//            return mgenc.assemble();

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
        NodeIterable<Node> graph2Iterator = graph2.getNodes();
        List<Node> graph2nodes = graph2Iterator.stream().toList();
        int i = 0;
        for (Node n: graph1.getNodes()) {
            System.out.println(n);
            System.out.println(graph2nodes.get(i));
            System.out.println("...");
        }
        graph1.getNodeCount();
        return false;
    }

    @Test
    public void testLoopConditionProfile() {
        // Must not compile immediately, the profile is not initialized until the first execution.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());
        this.getContext().eval(SomLanguage.INIT);

        // deactivates execution before compilation
        this.preventProfileCalls = true;

        String squareCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100 atRandom. l3 := l2 * l2. ^ l3 )";
//        String squareCodeStr = "test = ( | l1 l2 l3 l4 | l3 := l3 * l3. ^ l3 )";
        String intIncrementLocalCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100 atRandom. l2 := l2 + 42. ^ l3 )";
//        String intIncrementCodeStr = "test = ( | l1 l2 l3 l4 | l2 := 100 atRandom. l3 := l3 + l2. ^ l3 )";

        String codeStr = squareCodeStr;
//        String codeStr = "test: arg = ( | l3 | l3 := arg * arg. ^ l3 )";
        SInvokable sInvokableSn = parseMethodInvokable(codeStr, SUPERNODES_ON);
        SInvokable sInvokableOg = parseMethodInvokable(codeStr, NO_SUPERNODES);
//        SequenceNode seqSn = (SequenceNode) parseMethodExpression(codeStr, SUPERNODES_ON);
//        SequenceNode seqOg = (SequenceNode) parseMethodExpression(codeStr, NO_SUPERNODES);

//        ExpressionNode supernodeExpr = readSequenceExpressions(seqSn, "expressions", ExpressionNode[].class)[0];
//        assertThat(supernodeExpr, instanceOf(LocalVariableSquareNode.class));

//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode((LocalVariableSquareNode) supernodeExpr);
//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode(seq);
// Need something that can be called, so something that inherits from RootNode
//        OptimizedCallTarget target = (OptimizedCallTarget) testSupernodeRootNode.getCallTarget();

        StructuredGraph graphOg = partialEval((OptimizedCallTarget) sInvokableOg.getCallTarget(), new Object[0]);
        StructuredGraph graphSn = partialEval((OptimizedCallTarget) sInvokableSn.getCallTarget(), new Object[0]);

        System.out.println(graphSn + "\n" + graphOg);
//        System.out.println(graphSn.getMethods() == graphOg.getMethods());
        System.out.println(graphSn.getMethods().size() == graphOg.getMethods().size());

        ControlFlowGraph cfg = ControlFlowGraph.compute(graphSn, true, false, false, false);
        System.out.println(Arrays.toString(cfg.getBlocks()));

        System.out.println(compareStructuredGraphs(graphOg, graphSn));
    }
}