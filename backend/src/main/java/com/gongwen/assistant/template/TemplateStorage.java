package com.gongwen.assistant.template;

import java.io.IOException;

public interface TemplateStorage {
    String save(String originalFileName, String fileExtension, byte[] content) throws IOException;
}
