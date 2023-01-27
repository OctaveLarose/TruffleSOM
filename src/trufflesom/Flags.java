package trufflesom;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class Flags {
    @CompilationFinal
    public static final boolean disableOptBlockNodesWithoutContext = true;
}
