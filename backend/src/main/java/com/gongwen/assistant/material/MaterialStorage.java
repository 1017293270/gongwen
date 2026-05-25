package com.gongwen.assistant.material;

import java.io.IOException;

public interface MaterialStorage {
    String save(long draftId, String originalFileName, String fileExtension, byte[] content) throws IOException;
}
