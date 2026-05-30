package com.gongwen.assistant.exporting;

import java.io.IOException;

public interface ExportStorage {
    String save(String fileName, byte[] content) throws IOException;
}
