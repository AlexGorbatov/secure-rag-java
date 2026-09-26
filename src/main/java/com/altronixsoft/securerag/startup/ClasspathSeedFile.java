package com.altronixsoft.securerag.startup;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Adapts bytes read from a classpath resource to {@link MultipartFile}, so seed documents go through
 * the same {@code DocumentIngestionService} path as a real upload instead of a second, untested route.
 */
final class ClasspathSeedFile implements MultipartFile {

    private final String filename;
    private final byte[] content;

    ClasspathSeedFile(String filename, byte[] content) {
        this.filename = filename;
        this.content = content;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    public byte[] getBytes() {
        return content;
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.write(dest.toPath(), content);
    }

}
