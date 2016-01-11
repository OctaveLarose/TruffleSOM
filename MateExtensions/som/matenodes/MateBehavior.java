package som.matenodes;

import som.interpreter.nodes.ISuperReadNode;
import som.matenodes.MateAbstractReflectiveDispatch.MateAbstractStandardDispatch;
import som.matenodes.MateAbstractReflectiveDispatchFactory.MateCachedDispatchMessageLookupNodeGen;
import som.matenodes.MateAbstractReflectiveDispatchFactory.MateCachedDispatchSuperMessageLookupNodeGen;
import som.matenodes.MateAbstractReflectiveDispatchFactory.MateDispatchFieldAccessNodeGen;
import som.matenodes.MateAbstractSemanticNodes.MateSemanticCheckNode;
import som.vm.constants.ReflectiveOp;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

public interface MateBehavior {

  public abstract MateSemanticCheckNode getMateNode();
  public abstract MateAbstractStandardDispatch getMateDispatch();
  public abstract void setMateNode(MateSemanticCheckNode node);
  public abstract void setMateDispatch(MateAbstractStandardDispatch node);

  public default Object doMateSemantics(final VirtualFrame frame,
      final Object[] arguments) {
    return this.getMateDispatch().executeDispatch(frame,
        this.getMateNode().execute(frame, arguments), arguments);
  }
  
  public default void initializeMateSemantics(SourceSection source, ReflectiveOp operation){
    this.setMateNode(MateSemanticCheckNode.createForFullCheck(source, operation));
  }
  
  public default void initializeMateDispatchForFieldAccess(SourceSection source){
    this.setMateDispatch(MateDispatchFieldAccessNodeGen.create(source));
  }
  
  public default void initializeMateDispatchForMessages(SourceSection source, SSymbol selector){
    this.setMateDispatch(MateCachedDispatchMessageLookupNodeGen.create(source, selector));
    //MateDispatchMessageLookupNodeGen.create(this.getSourceSection(), this.getSelector());
  }
  
  public default void initializeMateDispatchForSuperMessages(SourceSection source, SSymbol selector, ISuperReadNode node){
    this.setMateDispatch(MateCachedDispatchSuperMessageLookupNodeGen.create(source, selector, node));
  }
}