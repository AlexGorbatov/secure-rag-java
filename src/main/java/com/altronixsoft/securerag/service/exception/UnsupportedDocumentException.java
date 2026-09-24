package com.altronixsoft.securerag.service.exception;

import lombok.Getter;

@Getter
public class UnsupportedDocumentException extends RuntimeException{

    private final String documentType;

    public UnsupportedDocumentException(String documentType) {
        super("Document type " + documentType + " is not supported");
        this.documentType = documentType;
    }
}
