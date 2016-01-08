package trufflesom.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;

import trufflesom.interpreter.nodes.nary.BinaryExpressionNode;
import trufflesom.vm.Universe;
import trufflesom.vm.constants.Nil;
import trufflesom.vmobjects.SBlock;
import trufflesom.vmobjects.SInvokable;


public abstract class WhileCache extends BinaryExpressionNode {

  public static final int INLINE_CACHE_SIZE = 6;

  protected final boolean     predicateBool;
  private final DynamicObject trueObject;
  private final DynamicObject falseObject;

  public WhileCache(final boolean predicateBool, final Universe universe) {
    this.predicateBool = predicateBool;
    this.trueObject = universe.getTrueObject();
    this.falseObject = universe.getFalseObject();
  }

  @Specialization(limit = "INLINE_CACHE_SIZE",
      guards = {"loopCondition.getMethod() == cachedLoopCondition",
          "loopBody.getMethod() == cachedLoopBody"})
  public final DynamicObject doCached(final SBlock loopCondition, final SBlock loopBody,
      @Cached("loopCondition.getMethod()") final SInvokable cachedLoopCondition,
      @Cached("loopBody.getMethod()") final SInvokable cachedLoopBody,
      @Cached("create(loopCondition, loopBody, predicateBool)") final WhileWithDynamicBlocksNode whileNode) {
    return whileNode.doWhileUnconditionally(loopCondition, loopBody);
  }

  private boolean obj2bool(final Object o) {
    if (o instanceof Boolean) {
      return (boolean) o;
    } else if (o == trueObject) {
      CompilerAsserts.neverPartOfCompilation("obj2Bool1");
      return true;
    } else {
      CompilerAsserts.neverPartOfCompilation("obj2Bool2");
      assert o == falseObject;
      return false;
    }
  }

  @Specialization(replaces = "doCached")
  public final DynamicObject doUncached(final VirtualFrame frame, final SBlock loopCondition,
      final SBlock loopBody) {
    // no caching, direct invokes, no loop count reporting...
    CompilerAsserts.neverPartOfCompilation("WhileCache.GenericDispatch");

    Object conditionResult = loopCondition.getMethod().invoke(new Object[] {loopCondition});

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    boolean loopConditionResult = obj2bool(conditionResult);

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == predicateBool) {
      loopBody.getMethod().invoke(new Object[] {loopBody});
      conditionResult = loopCondition.getMethod().invoke(new Object[] {loopCondition});
      loopConditionResult = obj2bool(conditionResult);
    }
    return Nil.nilObject;
  }
}
