package com.gongwen.assistant.exporting.word;

public class MissingTemplateValueException extends RuntimeException {
    public MissingTemplateValueException(String placeholder) {
        super("缺少模板字段值：" + placeholder);
    }
}
