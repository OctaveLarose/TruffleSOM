package trufflesom.vm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public final class OptimizationFlags {
    @CompilationFinal
    public static final boolean disableOptBlockNodesWithoutContext = System.getProperty("opt.disableBlocksWithoutContext") != null;

    @CompilationFinal
    public static final boolean disableInlineCaching = System.getProperty("opt.disableInlineCaching") != null;

    @CompilationFinal
    public static final boolean disableGlobalCaching = System.getProperty("opt.disableGlobalCaching") != null;

    @CompilationFinal
    public static final boolean disableInliningNodes = System.getProperty("opt.disableInliningNodes") != null;
}
