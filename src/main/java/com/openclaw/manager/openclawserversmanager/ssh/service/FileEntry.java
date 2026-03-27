package com.openclaw.manager.openclawserversmanager.ssh.service;

public record FileEntry(
        String name,
        String path,
        boolean directory,
        long size,
        long mtime
) {}
