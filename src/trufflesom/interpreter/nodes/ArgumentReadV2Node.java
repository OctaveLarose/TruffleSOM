package trufflesom.interpreter.nodes;

import bdt.inlining.ScopeAdaptationVisitor;
import bdt.tools.nodes.Invocation;
import com.oracle.truffle.api.frame.VirtualFrame;
import trufflesom.compiler.Variable.Argument;
import trufflesom.vmobjects.SSymbol;


public abstract class ArgumentReadV2Node {

  public static class LocalArgumentReadNode extends NoPreEvalExprNode
      implements Invocation<SSymbol> {
    public final int      argumentIndex;
    public final Argument arg;

    public LocalArgumentReadNode(final Argument arg) {
      assert arg.index >= 0;
      this.arg = arg;
      this.argumentIndex = arg.index;

      if (arg.index > 10000) {
        // if these methods are never called, I can't use them to replace existing calls... bad hack
        this.doLong(null);
        this.doDouble(null);
      }
    }

    /** Only to be used in primitives. */
    public LocalArgumentReadNode(final boolean useInPrim, final int idx) {
      assert idx >= 0;
      this.arg = null;
      this.argumentIndex = idx;
      assert useInPrim;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return frame.getArguments()[argumentIndex];
    }

    public final long doLong(final VirtualFrame frame) {
      return (long) frame.getArguments()[argumentIndex];
    }

    public final double doDouble(final VirtualFrame frame) {
      return (double) frame.getArguments()[argumentIndex];
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    public boolean isSelfRead() {
      return argumentIndex == 0;
    }

    @Override
    public String toString() {
      String argId;
      if (arg == null) {
        argId = "" + argumentIndex;
      } else {
        argId = arg.name.getString();
      }
      return "ArgRead(" + argId + ")";
    }
  }

  public static class LocalArgumentWriteNode extends NoPreEvalExprNode {
    protected final int   argumentIndex;
    public final Argument arg;

    @Child protected ExpressionNode valueNode;

    public LocalArgumentWriteNode(final Argument arg, final ExpressionNode valueNode) {
      assert arg.index >= 0;
      this.arg = arg;
      this.argumentIndex = arg.index;
      this.valueNode = valueNode;
    }

    /** Only to be used in primitives. */
    public LocalArgumentWriteNode(final boolean useInPrim, final int idx,
        final ExpressionNode valueNode) {
      assert idx >= 0;
      this.arg = null;
      this.argumentIndex = idx;
      assert useInPrim;
      this.valueNode = valueNode;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object value = valueNode.executeGeneric(frame);
      frame.getArguments()[argumentIndex] = value;
      return value;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(arg, this, valueNode, 0);
    }
  }

  public static class NonLocalArgumentReadNode extends ContextualNode
      implements Invocation<SSymbol> {
    protected final int   argumentIndex;
    public final Argument arg;

    public NonLocalArgumentReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      this.arg = arg;
      this.argumentIndex = arg.index;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return determineContext(frame).getArguments()[argumentIndex];
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public String toString() {
      String argId;
      if (arg == null) {
        argId = "" + argumentIndex;
      } else {
        argId = arg.name.getString();
      }
      return "ArgRead(" + argId + ", ctx: " + contextLevel + ")";
    }
  }

  public static class NonLocalArgumentWriteNode extends ContextualNode {
    protected final int   argumentIndex;
    public final Argument arg;

    @Child protected ExpressionNode valueNode;

    public NonLocalArgumentWriteNode(final Argument arg, final int contextLevel,
        final ExpressionNode valueNode) {
      super(contextLevel);
      assert contextLevel > 0;
      this.arg = arg;
      this.argumentIndex = arg.index;

      this.valueNode = valueNode;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object value = valueNode.executeGeneric(frame);
      determineContext(frame).getArguments()[argumentIndex] = value;
      return value;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(arg, this, valueNode, contextLevel);
    }
  }
}