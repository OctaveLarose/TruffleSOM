package trufflesom.interpreter;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bdt.inlining.nodes.WithSource;
import bdt.primitives.nodes.PreevaluatedExpression;
import bdt.source.SourceCoordinate;
import trufflesom.compiler.MethodGenerationContext;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.dispatch.AbstractDispatchNode;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable.SMethod;


public abstract class Invokable extends RootNode implements WithSource {
  protected final String name;
  protected final Source source;
  protected final long   sourceCoord;

  protected SClass holder;

  protected Invokable(final String name, final Source source, final long sourceCoord,
      final FrameDescriptor frameDescriptor) {
    super(SomLanguage.getCurrent(), frameDescriptor);
    this.name = name;
    this.source = source;
    this.sourceCoord = sourceCoord;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Invokable initialize(final long sourceCoord) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Source getSource() {
    return source;
  }

  @Override
  public boolean hasSource() {
    return true;
  }

  @Override
  public long getSourceCoordinate() {
    return sourceCoord;
  }

  @Override
  public SourceSection getSourceSection() {
    return SourceCoordinate.createSourceSection(source, sourceCoord);
  }

  /** Inline invokable into the lexical context of the target method generation context. */
  public abstract ExpressionNode inline(MethodGenerationContext targetMgenc,
      SMethod toBeInlined);

  @Override
  public final boolean isCloningAllowed() {
    return true;
  }

  @Override
  protected boolean isCloneUninitializedSupported() {
    return true;
  }

  @Override
  protected RootNode cloneUninitialized() {
    return (RootNode) deepCopy();
  }

  public abstract void propagateLoopCountThroughoutLexicalScope(long count);

  public SClass getHolder() {
    return holder;
  }

  public void setHolder(final SClass holder) {
    this.holder = holder;
  }

  @Override
  public abstract boolean isTrivial();

  public PreevaluatedExpression copyTrivialNode() {
    return null;
  }

  public AbstractDispatchNode asDispatchNode(final Object rcvr,
      final AbstractDispatchNode next) {
    return null;
  }
}
