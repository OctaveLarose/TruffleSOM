package trufflesom.vm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class OptimizationFlags {
    @CompilationFinal
    public static final boolean disableOptBlockNodesWithoutContext = System.getProperty("opt.disableBlocksWithoutContext") != null;
}
