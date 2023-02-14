package ru.nsu.ccfit.arkhipov.util;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ExecutableState {
    void execute(SelectionKey selectionKey) throws IOException;
}
