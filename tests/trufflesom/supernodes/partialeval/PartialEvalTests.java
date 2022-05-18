package trufflesom.supernodes.partialeval;

import bd.basic.ProgramDefinitionError;
import bd.source.SourceCoordinate;
import bd.tools.structure.StructuralProbe;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.TruffleDebugJavaMethod;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;
import org.graalvm.compiler.truffle.compiler.PerformanceInformationHandler;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;

import org.graalvm.compiler.truffle.test.LoopNodePartialEvaluationTest;
import org.graalvm.compiler.truffle.test.PartialEvaluationTest;

import org.graalvm.polyglot.Context;
import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.ProfileData;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import trufflesom.compiler.*;
import trufflesom.interpreter.SomLanguage;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.SequenceNode;
import trufflesom.interpreter.objectstorage.StorageAnalyzer;
import trufflesom.interpreter.supernodes.LocalVariableSquareNode;
import trufflesom.interpreter.supernodes.LocalVariableSquareNodeGen;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SSymbol;

import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static trufflesom.vm.SymbolTable.symSelf;
import static trufflesom.vm.SymbolTable.symbolFor;

public class PartialEvalTests extends PartialEvaluationTest {
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
        }

        @Override
        public Object execute(VirtualFrame frame) {
//            return this.superNode.executeGeneric(frame);
            frame.setLong(1, 10);
//            frame.getArguments()
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

    protected ExpressionNode parseMethod(final String source) {
        Source s = SomLanguage.getSyntheticSource(source, "test");

        cgenc = new ClassGenerationContext(s, null);
        cgenc.setName(symbolFor("Test"));
//        addAllFields();

        mgenc = new MethodGenerationContext(cgenc, (StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable>) null);
        mgenc.addArgumentIfAbsent(symSelf, 0);

        ParserAst parser = new ParserAst(source, s, null);
        try {
            return parser.method(mgenc);
        } catch (ProgramDefinitionError e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T read(final Object obj, final String fieldName, final Class<T> c) {
        java.lang.reflect.Field field = lookup(obj.getClass(), fieldName);
        field.setAccessible(true);
        try {
            return c.cast(field.get(obj));
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field lookup(final Class<?> cls, final String fieldName) {
        try {
            return cls.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (cls.getSuperclass() != null) {
                return lookup(cls.getSuperclass(), fieldName);
            }
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Didn't find field: " + fieldName);
    }

    @Test
    public void testLoopConditionProfile() {
        // Must not compile immediately, the profile is not initialized until the first execution.
        setupContext(Context.newBuilder().allowExperimentalOptions(true).option("engine.CompileImmediately", "false").option("engine.BackgroundCompilation", "false").build());
        this.getContext().eval(SomLanguage.INIT);
        this.preventProfileCalls = true;

        //        Assert.assertEquals(42, 42);
        String test = "l2 * l2.";
        SequenceNode seq = (SequenceNode) parseMethod("test = ( | l1 l2 l3 l4 | \n" + test + " )");
        ExpressionNode supernodeExpr = read(seq, "expressions", ExpressionNode[].class)[0];

        assertThat(supernodeExpr, instanceOf(LocalVariableSquareNode.class));
//        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode((LocalVariableSquareNode) supernodeExpr);
        TestSupernodeRootNode testSupernodeRootNode = new TestSupernodeRootNode(seq);

        // Need something that can be called, so something that inherits from RootNode
        OptimizedCallTarget target = (OptimizedCallTarget) testSupernodeRootNode.getCallTarget();
        StructuredGraph graph = partialEval(target, new Object[0]);

//        Stream<IfNode> ifWithInjectedProfile = graph.getNodes()
//                .filter(IfNode.class).stream()
//                .filter(i -> i.getProfileData().getProfileSource() == ProfileData.ProfileSource.INJECTED);
//        IfNode ifNode = ifWithInjectedProfile.findFirst().orElseThrow(() -> new AssertionError("If with injected branch probability not found"));
//        Assert.assertEquals("Expected true successor probability", 0.9, ifNode.getTrueSuccessorProbability(), 0.01);

        List<LoopBeginNode> loopBegins = graph.getNodes().filter(LoopBeginNode.class).snapshot();
        Assert.assertEquals(loopBegins.toString(), 1, loopBegins.size());
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, false, false);
        for (LoopBeginNode loopBegin : loopBegins) {
            Assert.assertEquals("Expected loop frequency", 10.0, cfg.localLoopFrequency(loopBegin), 0.01);
        }
    }
}