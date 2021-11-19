package trufflesom.interpreter.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Specializer;
import bd.primitives.nodes.PreevaluatedExpression;
import bd.tools.nodes.Invocation;
import trufflesom.interpreter.TruffleCompiler;
import trufflesom.interpreter.nodes.dispatch.AbstractDispatchNode;
import trufflesom.interpreter.nodes.dispatch.DispatchChain.Cost;
import trufflesom.interpreter.nodes.dispatch.GenericDispatchNode;
import trufflesom.interpreter.nodes.dispatch.UninitializedDispatchNode;
import trufflesom.interpreter.nodes.nary.EagerlySpecializableNode;
import trufflesom.primitives.Primitives;
import trufflesom.vm.NotYetImplementedException;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SSymbol;


public final class MessageSendNode {

  public static ExpressionNode create(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source, final Universe universe) {
    Primitives prims = universe.getPrimitives();
    Specializer<Universe, ExpressionNode, SSymbol> specializer =
        prims.getParserSpecializer(selector, arguments);
    if (specializer == null) {
      return new UninitializedMessageSendNode(
          selector, arguments, universe).initialize(source);
    }

    EagerlySpecializableNode newNode = (EagerlySpecializableNode) specializer.create(null,
        arguments, source, !specializer.noWrapper(), universe);

    if (specializer.noWrapper()) {
      return newNode;
    } else {
      return newNode.wrapInEagerWrapper(selector, arguments, universe);
    }
  }

  private static final ExpressionNode[] NO_ARGS = new ExpressionNode[0];

  public static AbstractMessageSendNode createForPerformNodes(final SSymbol selector,
      final SourceSection source, final Universe universe) {
    return new UninitializedMessageSendNode(selector, NO_ARGS, universe).initialize(source);
  }

  public static GenericMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source,
      final Universe universe) {
    return new GenericMessageSendNode(selector, argumentNodes,
        new UninitializedDispatchNode(selector, universe)).initialize(source);
  }

  public static AbstractMessageSendNode createSuperSend(final SClass superClass,
      final SSymbol selector, final ExpressionNode[] arguments, final SourceSection source) {
    SInvokable method = superClass.lookupInvokable(selector);

    if (method == null) {
      throw new NotYetImplementedException(
          "Currently #dnu with super sent is not yet implemented. ");
    }

    if (method.isTrivial()) {
      PreevaluatedExpression node = method.copyTrivialNode();
      return new SuperExprNode(selector, arguments, node).initialize(source);
    }

    DirectCallNode superMethodNode = Truffle.getRuntime().createDirectCallNode(
        method.getCallTarget());

    return new SuperSendNode(selector, arguments, superMethodNode).initialize(source);
  }

  public abstract static class AbstractMessageSendNode extends ExpressionNode
      implements PreevaluatedExpression, Invocation<SSymbol> {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
      this.argumentNodes = arguments;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = new Object[argumentNodes.length];
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
        assert arguments[i] != null;
      }
      return arguments;
    }
  }

  public static final class UninitializedMessageSendNode extends AbstractMessageSendNode {

    protected final SSymbol  selector;
    protected final Universe universe;

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final Universe universe) {
      super(arguments);
      this.selector = selector;
      this.universe = universe;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(" + selector.getString() + ")";
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] arguments) {
      return specialize(arguments).doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      // We treat super sends separately for simplicity, might not be the
      // optimal solution, especially in cases were the knowledge of the
      // receiver class also allows us to do more specific things, but for the
      // moment we will leave it at this.
      // TODO: revisit, and also do more specific optimizations for super sends.

      Primitives prims = universe.getPrimitives();

      Specializer<Universe, ExpressionNode, SSymbol> specializer =
          prims.getEagerSpecializer(selector, arguments, argumentNodes);

      if (specializer != null) {
        boolean noWrapper = specializer.noWrapper();
        EagerlySpecializableNode newNode =
            (EagerlySpecializableNode) specializer.create(arguments, argumentNodes,
                sourceSection, !noWrapper, universe);
        if (noWrapper) {
          return replace(newNode);
        } else {
          return makeEagerPrim(newNode);
        }
      }

      return makeGenericSend();
    }

    private PreevaluatedExpression makeEagerPrim(final EagerlySpecializableNode prim) {
      assert prim.getSourceSection() != null;

      PreevaluatedExpression result = (PreevaluatedExpression) replace(
          prim.wrapInEagerWrapper(selector, argumentNodes, universe));

      return result;
    }

    private GenericMessageSendNode makeGenericSend() {
      GenericMessageSendNode send = new GenericMessageSendNode(selector, argumentNodes,
          new UninitializedDispatchNode(selector, universe)).initialize(sourceSection);
      return replace(send);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return selector;
    }

  }

  // TODO: currently, we do not only specialize the given stuff above, but also what has been
  // classified as 'value' sends in the OMOP branch. Is that a problem?

  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    @Child private AbstractDispatchNode dispatchNode;

    private GenericMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(arguments);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
    }

    public void replaceDispatchListHead(
        final GenericDispatchNode replacement) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      return "GMsgSend(" + selector.getString() + ")";
    }

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return selector;
    }
  }

  public static final class SuperSendNode extends AbstractMessageSendNode {
    private final SSymbol selector;

    @Child private DirectCallNode cachedSuperMethod;

    private SuperSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final DirectCallNode superMethod) {
      super(arguments);
      this.selector = selector;
      this.cachedSuperMethod = superMethod;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return cachedSuperMethod.call(arguments);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return selector;
    }

    @Override
    public String toString() {
      return "SuperSend(" + selector.getString() + ")";
    }
  }

  private static final class SuperExprNode extends AbstractMessageSendNode {
    private final SSymbol                 selector;
    @Child private PreevaluatedExpression expr;

    private SuperExprNode(final SSymbol selector, final ExpressionNode[] arguments,
        final PreevaluatedExpression expr) {
      super(arguments);
      this.selector = selector;
      this.expr = expr;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return expr.doPreEvaluated(frame, arguments);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return selector;
    }

    @Override
    public String toString() {
      return "SendExpr(" + selector.getString() + ")";
    }
  }
}
