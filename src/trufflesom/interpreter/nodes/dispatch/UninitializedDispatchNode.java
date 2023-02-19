package trufflesom.interpreter.nodes.dispatch;

import static com.oracle.truffle.api.CompilerDirectives.transferToInterpreterAndInvalidate;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import bdt.primitives.nodes.PreevaluatedExpression;
import trufflesom.interpreter.Types;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SObject;
import trufflesom.vmobjects.SSymbol;


public final class UninitializedDispatchNode extends AbstractDispatchNode {
  private final SSymbol selector;

  public UninitializedDispatchNode(final SSymbol selector) {
    this.selector = selector;
  }

  private AbstractDispatchNode specialize(final Object[] arguments) {
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector);
    this.replace(genericReplacement);
    return genericReplacement;
  }

  public static AbstractDispatchNode createDispatch(final Object rcvr, final SSymbol selector,
      final UninitializedDispatchNode newChainEnd) {
    SClass rcvrClass = Types.getClassOf(rcvr);
    SInvokable method = rcvrClass.lookupInvokable(selector);

    if (method == null) {
      DispatchGuard guard = DispatchGuard.create(rcvr);
      return new CachedDnuNode(rcvrClass, guard, selector, newChainEnd);
    }

    AbstractDispatchNode node = method.asDispatchNode(rcvr, newChainEnd);
    if (node != null) {
      return node;
    }

    PreevaluatedExpression expr = method.copyTrivialNode();

    DispatchGuard guard = DispatchGuard.create(rcvr);
    if (expr != null) {
      return new CachedExprNode(guard, expr, method.getSource(), newChainEnd);
    }

    CallTarget callTarget = method.getCallTarget();
    return new CachedDispatchNode(guard, callTarget, newChainEnd);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    transferToInterpreterAndInvalidate();
    return specialize(arguments).executeDispatch(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 0;
  }
}
