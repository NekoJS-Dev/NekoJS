package com.tkisor.nekojs.core.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public final class NekoNodeStats {
    private final BasicFileAttributes attributes;

    public NekoNodeStats(Path path, boolean followLinks) throws IOException {
        this.attributes = followLinks ? Files.readAttributes(path, BasicFileAttributes.class) : Files.readAttributes(path, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    public boolean isFile() { return attributes.isRegularFile(); }
    public boolean isDirectory() { return attributes.isDirectory(); }
    public boolean isSymbolicLink() { return attributes.isSymbolicLink(); }
    public boolean isOther() { return attributes.isOther(); }
    public boolean isBlockDevice() { return false; }
    public boolean isCharacterDevice() { return false; }
    public boolean isFIFO() { return false; }
    public boolean isSocket() { return false; }

    public long size() { return attributes.size(); }
    public long mtimeMs() { return millis(attributes.lastModifiedTime()); }
    public long ctimeMs() { return millis(attributes.lastModifiedTime()); }
    public long atimeMs() { return millis(attributes.lastAccessTime()); }
    public long birthtimeMs() { return millis(attributes.creationTime()); }

    // Extended POSIX fields (not available via BasicFileAttributes)
    public long dev() { return 0; }
    public long ino() { return 0; }
    public long mode() { return 0; }
    public long nlink() { return 0; }
    public long uid() { return 0; }
    public long gid() { return 0; }
    public long rdev() { return 0; }
    public long blksize() { return 0; }
    public long blocks() { return 0; }

    private static long millis(FileTime time) {
        return time == null ? 0L : time.toMillis();
    }
}
