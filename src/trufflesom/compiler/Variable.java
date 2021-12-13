package trufflesom.compiler;

import static trufflesom.compiler.bc.BytecodeGenerator.emitPOPARGUMENT;
import static trufflesom.compiler.bc.BytecodeGenerator.emitPOPLOCAL;
import static trufflesom.compiler.bc.BytecodeGenerator.emitPUSHARGUMENT;
import static trufflesom.compiler.bc.BytecodeGenerator.emitPUSHLOCAL;
import static trufflesom.interpreter.SNodeFactory.createArgumentRead;
import static trufflesom.interpreter.SNodeFactory.createArgumentWrite;
import static trufflesom.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import static trufflesom.vm.SymbolTable.symBlockSelf;
import static trufflesom.vm.SymbolTable.symSelf;
import static trufflesom.vm.SymbolTable.symbolFor;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;

import bd.source.SourceCoordinate;
import trufflesom.compiler.bc.BytecodeMethodGenContext;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.LocalVariableNodeFactory.LocalVariableReadNodeGen;
import trufflesom.interpreter.nodes.LocalVariableNodeFactory.LocalVariableWriteNodeGen;
import trufflesom.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableReadNodeGen;
import trufflesom.interpreter.nodes.NonLocalVariableNodeFactory.NonLocalVariableWriteNodeGen;
import trufflesom.vm.NotYetImplementedException;
import trufflesom.vmobjects.SSymbol;


public abstract class Variable implements bd.inlining.Variable<ExpressionNode> {
  public final SSymbol name;
  public final long    coord;

  Variable(final SSymbol name, final long coord) {
    this.name = name;
    this.coord = coord;
  }

  public final SSymbol getName() {
    return name;
  }

  /** Gets the name including lexical location. */
  public final SSymbol getQualifiedName(final Source source) {
    return symbolFor(name.getString() + SourceCoordinate.getLocationQualifier(source, coord));
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + name + ")";
  }

  @Override
  public abstract ExpressionNode getReadNode(int contextLevel, long coord);

  protected abstract void emitPop(BytecodeMethodGenContext mgenc);

  protected abstract void emitPush(BytecodeMethodGenContext mgenc);

  public abstract Variable split(FrameDescriptor descriptor);

  public abstract Local splitToMergeIntoOuterScope(FrameDescriptor descriptor);

  @Override
  public boolean equals(final Object o) {
    assert o != null;
    if (o == this) {
      return true;
    }
    if (!(o instanceof Variable)) {
      return false;
    }
    Variable var = (Variable) o;
    if (var.coord == coord) {
      assert name == var.name : "Defined in the same place, but names not equal?";
      return true;
    }
    assert coord == 0
        || coord != var.coord : "Why are there multiple objects for this source section? might need to fix comparison above";
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, coord);
  }

  public static final class Argument extends Variable {
    public final int index;

    Argument(final SSymbol name, final int index, final long coord) {
      super(name, coord);
      this.index = index;
    }

    public boolean isSelf() {
      return symSelf == name || symBlockSelf == name;
    }

    @Override
    public Variable split(final FrameDescriptor descriptor) {
      return this;
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      if (isSelf()) {
        return null;
      }

      Local l = new Local(name, coord);
      l.init(descriptor.addFrameSlot(l), descriptor);
      return l;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel, final long coord) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      return createArgumentRead(this, contextLevel, coord);
    }

    @Override
    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr, final long coord) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      return createArgumentWrite(this, contextLevel, valueExpr, coord);
    }

    @Override
    public void emitPop(final BytecodeMethodGenContext mgenc) {
      emitPOPARGUMENT(mgenc, (byte) index, (byte) mgenc.getContextLevel(this));
    }

    @Override
    protected void emitPush(final BytecodeMethodGenContext mgenc) {
      emitPUSHARGUMENT(mgenc, (byte) index, (byte) mgenc.getContextLevel(this));
    }
  }

  public static final class Local extends Variable {
    @CompilationFinal private transient FrameSlot slot;
    @CompilationFinal private FrameDescriptor     descriptor;

    Local(final SSymbol name, final long coord) {
      super(name, coord);
    }

    public void init(final FrameSlot slot, final FrameDescriptor descriptor) {
      this.slot = slot;
      this.descriptor = descriptor;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel, final long coord) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      if (contextLevel > 0) {
        return NonLocalVariableReadNodeGen.create(contextLevel, this).initialize(coord);
      }
      return LocalVariableReadNodeGen.create(this).initialize(coord);
    }

    public FrameSlot getSlot() {
      return slot;
    }

    @Override
    public Local split(final FrameDescriptor descriptor) {
      Local newLocal = new Local(name, coord);
      newLocal.init(descriptor.addFrameSlot(newLocal), descriptor);
      return newLocal;
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      return split(descriptor);
    }

    @Override
    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr, final long coord) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      if (contextLevel > 0) {
        return NonLocalVariableWriteNodeGen.create(contextLevel, this, valueExpr)
                                           .initialize(coord);
      }
      return LocalVariableWriteNodeGen.create(this, valueExpr).initialize(coord);
    }

    public FrameDescriptor getFrameDescriptor() {
      return descriptor;
    }

    @Override
    public void emitPop(final BytecodeMethodGenContext mgenc) {
      int contextLevel = mgenc.getContextLevel(this);
      emitPOPLOCAL(mgenc, mgenc.getLocalIndex(this, contextLevel), (byte) contextLevel);
    }

    @Override
    public void emitPush(final BytecodeMethodGenContext mgenc) {
      int contextLevel = mgenc.getContextLevel(this);
      emitPUSHLOCAL(mgenc, mgenc.getLocalIndex(this, contextLevel), (byte) contextLevel);
    }
  }

  public static final class Internal extends Variable {
    @CompilationFinal private FrameSlot       slot;
    @CompilationFinal private FrameDescriptor descriptor;

    public Internal(final SSymbol name, final long coord) {
      super(name, coord);
    }

    public void init(final FrameSlot slot, final FrameDescriptor descriptor) {
      assert this.slot == null && slot != null;

      this.slot = slot;
      this.descriptor = descriptor;
    }

    public FrameSlot getSlot() {
      assert slot != null : "Should have been initialized with init(.)";
      return slot;
    }

    @Override
    public ExpressionNode getReadNode(final int contextLevel, final long coord) {
      throw new UnsupportedOperationException(
          "There shouldn't be any language-level read nodes for internal slots. "
              + "They are used directly by other nodes.");
    }

    @Override
    public Variable split(final FrameDescriptor descriptor) {
      Internal newInternal = new Internal(name, coord);

      assert this.descriptor.getFrameSlotKind(
          slot) == FrameSlotKind.Object : "We only have the on stack marker currently, so, we expect those not to specialize";
      newInternal.init(descriptor.addFrameSlot(newInternal, FrameSlotKind.Object), descriptor);
      return newInternal;
    }

    @Override
    public Local splitToMergeIntoOuterScope(final FrameDescriptor descriptor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void emitPop(final BytecodeMethodGenContext mgenc) {
      throw new NotYetImplementedException();
    }

    @Override
    public void emitPush(final BytecodeMethodGenContext mgenc) {
      throw new NotYetImplementedException();
    }

    @Override
    public boolean equals(final Object o) {
      assert o != null;
      if (o == this) {
        return true;
      }
      if (!(o instanceof Variable)) {
        return false;
      }
      Variable var = (Variable) o;
      return var.coord == coord && name == var.name;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, coord);
    }
  }
}
