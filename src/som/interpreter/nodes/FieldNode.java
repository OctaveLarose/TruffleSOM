/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.interpreter.nodes;

import som.interpreter.nodes.MateAbstractExpressionNodeGen.MateFieldNodeGen;
import som.interpreter.nodes.MateAbstractReceiverNode.MateReceiverExpressionNode;
import som.interpreter.nodes.MateAbstractSemanticCheckNodeFactory.MateEnvironmentSemanticCheckNodeGen;
import som.interpreter.nodes.MateAbstractSemanticCheckNodeFactory.MateObjectSemanticCheckNodeGen;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedWriteFieldNode;
import som.vm.constants.ReflectiveOp;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

public abstract class FieldNode extends ExpressionWithReceiverNode {

  protected FieldNode(final SourceSection source) {
    super(source);
  }

  protected abstract ExpressionNode getSelf();
  public abstract Object[] argumentsForReceiver(final VirtualFrame frame, SObject receiver);

  @Override
  public void wrapIntoMateNode() {
    replace(MateFieldNodeGen.create((FieldNode) this,
        new MateReceiverExpressionNode(this),
        MateEnvironmentSemanticCheckNodeGen.create(),
        MateObjectSemanticCheckNodeGen.create()));
  }

  public Object[] evaluateArguments(final VirtualFrame frame) {
    SObject object;
    try {
      object = this.getSelf().executeSObject(frame);
    } catch (UnexpectedResultException e) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never happen by construction");
    }
    return this.argumentsForReceiver(frame, object);
  }
  
  public static final class FieldReadNode extends FieldNode
      implements PreevaluatedExpression {
    @Child private ExpressionNode self;
    @Child private AbstractReadFieldNode read;
   
    public FieldReadNode(final ExpressionNode self, final int fieldIndex,
        final SourceSection source) {
      super(source);
      this.self = self;
      read = new UninitializedReadFieldNode(fieldIndex);
    }

    public FieldReadNode(final FieldReadNode node) {
      this(node.self, node.read.getFieldIndex(), node.getSourceSection());
    }

    @Override
    protected ExpressionNode getSelf() {
      return self;
    }

    public Object executeEvaluated(final SObject obj) {
       return read.read(obj);
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated((SObject) arguments[0]);
    }

    @Override
    public long executeLong(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return read.readLong(obj);
    }

    @Override
    public double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return read.readDouble(obj);
    }

    @Override
    public Object[] argumentsForReceiver(VirtualFrame frame, SObject receiver) {
      Object[] arguments = new Object[2];
      arguments[0] = receiver;
      arguments[1] = this.read.getFieldIndex();
      return arguments;
    }
    
    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return executeEvaluated((SObject)this.evaluateArguments(frame)[0]);
    }
    
    public ReflectiveOp reflectiveOperation(){
      return ReflectiveOp.ReadField;
    }

    @Override
    public ExpressionNode getReceiver() {
      return this.getSelf();
    }
  }

  @NodeChildren({
    @NodeChild(value = "self", type = ExpressionNode.class),
    @NodeChild(value = "value", type = ExpressionNode.class)})
  public abstract static class FieldWriteNode extends FieldNode
      implements PreevaluatedExpression {
    @Child private AbstractWriteFieldNode write;

    protected abstract ExpressionNode getValue();
    
    public FieldWriteNode(final int fieldIndex, final SourceSection source) {
      super(source);
      write = new UninitializedWriteFieldNode(fieldIndex);
    }

    public FieldWriteNode(final FieldWriteNode node) {
      this(node.write.getFieldIndex(), node.getSourceSection());
    }

    public final Object executeEvaluated(final VirtualFrame frame,
        final SObject self, final Object value) {
      return write.write(self, value);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated(frame, (SObject) arguments[0], arguments[1]);
    }

    @Specialization
    public long doLong(final VirtualFrame frame, final SObject self,
        final long value) {
      return write.write(self, value);
    }

    @Specialization
    public double doDouble(final VirtualFrame frame, final SObject self,
        final double value) {
      return write.write(self, value);
    }

    @Specialization
    public Object doObject(final VirtualFrame frame, final SObject self,
        final Object value) {
      return executeEvaluated(frame, self, value);
    }
    
    public Object[] argumentsForReceiver(final VirtualFrame frame, SObject receiver) {
      Object[] arguments = new Object[3];
      arguments[0] = receiver;
      arguments[1] = this.write.getFieldIndex();
      arguments[2] = this.getValue().executeGeneric(frame);
      return arguments;
    }
    
    @Override
    public ExpressionNode getReceiver() {
      return this.getSelf();
    }
    
    public ReflectiveOp reflectiveOperation(){
      return ReflectiveOp.WriteField;
    }
    
    public Object[] evaluateArguments(final VirtualFrame frame) {
      SObject object;
      try {
        object = this.getSelf().executeSObject(frame);
      } catch (UnexpectedResultException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException("This should never happen by construction");
      }
      return this.argumentsForReceiver(frame, object);
    }
  }
}
