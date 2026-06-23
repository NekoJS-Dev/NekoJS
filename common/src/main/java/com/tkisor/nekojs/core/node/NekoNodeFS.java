package com.tkisor.nekojs.core.node;

import com.tkisor.nekojs.core.fs.ClassFilter;
import com.tkisor.nekojs.core.fs.NekoJSPaths;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NekoNodeFS {
    private Path currentWorkingDirectory = NekoJSPaths.GAME_DIR;

    public synchronized String cwd() {
        return currentWorkingDirectory.toString();
    }

    public synchronized void chdir(String path) throws IOException {
        Path target = resolveRead(path);
        if (!Files.isDirectory(target)) {
            throw new NotDirectoryException(target.toString());
        }
        currentWorkingDirectory = target;
    }

    public synchronized boolean existsSync(String path) {
        try {
            return Files.exists(resolveRead(path));
        } catch (IOException e) {
            return false;
        }
    }

    public synchronized NekoNodeBuffer readFileBuffer(String path) throws IOException {
        return NekoNodeBuffer.fromBytes(Files.readAllBytes(resolveRead(path)));
    }

    public synchronized String readFileString(String path, String encoding) throws IOException {
        return new String(Files.readAllBytes(resolveRead(path)), NekoNodeBuffer.charset(encoding));
    }

    public synchronized void writeFile(String path, String data, String encoding) throws IOException {
        Path target = resolveWrite(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(verifyWriteParentForCreate(parent));
        }
        Files.writeString(target, data == null ? "" : data, NekoNodeBuffer.charset(encoding));
    }

    public synchronized void writeFileBuffer(String path, NekoNodeBuffer data) throws IOException {
        Path target = resolveWrite(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(verifyWriteParentForCreate(parent));
        }
        Files.write(target, data == null ? new byte[0] : data.bytes());
    }

    public synchronized void appendFile(String path, String data, String encoding) throws IOException {
        Path target = resolveWrite(path);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(verifyWriteParentForCreate(parent));
        }
        Files.writeString(target, data == null ? "" : data, NekoNodeBuffer.charset(encoding), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void mkdir(String path, boolean recursive) throws IOException {
        Path target = resolveWrite(path);
        if (recursive) {
            Files.createDirectories(target);
        } else {
            Files.createDirectory(target);
        }
    }

    public synchronized void rm(String path, boolean recursive, boolean force) throws IOException {
        Path target;
        try {
            target = resolveWriteExisting(path);
        } catch (NoSuchFileException e) {
            if (force) return;
            throw e;
        }
        if (recursive && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.walk(target)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path value : paths) {
                    Files.deleteIfExists(value);
                }
            }
        } else if (force) {
            Files.deleteIfExists(target);
        } else {
            Files.delete(target);
        }
    }

    public synchronized void unlink(String path) throws IOException {
        Files.delete(resolveWriteExisting(path));
    }

    public synchronized List<String> readdir(String path) throws IOException {
        Path dir = resolveRead(path);
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                NekoJSPaths.legacy().verifyInsideGameDir(entry);
                Path fileName = entry.getFileName();
                if (fileName != null) {
                    result.add(fileName.toString());
                }
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    public synchronized void access(String path, int mode) throws IOException {
        Path target = resolveRead(path);
        if (!Files.exists(target)) {
            throw new NoSuchFileException(target.toString());
        }
        if ((mode & 4) != 0 && !Files.isReadable(target)) {
            throw new AccessDeniedException(target.toString());
        }
        if ((mode & 2) != 0 && !Files.isWritable(target)) {
            throw new AccessDeniedException(target.toString());
        }
        if ((mode & 1) != 0 && !Files.isExecutable(target)) {
            throw new AccessDeniedException(target.toString());
        }
    }

    public synchronized NekoNodeStats stat(String path) throws IOException {
        return new NekoNodeStats(resolveRead(path), true);
    }

    public synchronized NekoNodeStats lstat(String path) throws IOException {
        Path target = NekoJSPaths.legacy().resolveGamePath(String.valueOf(path), currentWorkingDirectory);
        return new NekoNodeStats(target, false);
    }

    public synchronized void rename(String oldPath, String newPath) throws IOException {
        Files.move(resolveWriteExisting(oldPath), resolveWrite(newPath), StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized void copyFile(String source, String destination) throws IOException {
        Files.copy(resolveRead(source), resolveWrite(destination), StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized String realpath(String path) throws IOException {
        return resolveRead(path).toRealPath().toString();
    }

    public synchronized String readlink(String path) throws IOException {
        Path link = NekoJSPaths.legacy().resolveGamePath(String.valueOf(path), currentWorkingDirectory);
        return Files.readSymbolicLink(link).toString();
    }

    private Path resolveRead(String path) throws IOException {
        return NekoJSPaths.legacy().resolveGamePath(String.valueOf(path), currentWorkingDirectory);
    }

    private Path resolveWrite(String path) throws IOException {
        if (ClassFilter.isAllowFsWriteOutsideNekojs()) {
            return NekoJSPaths.legacy().resolveGamePathForCreate(String.valueOf(path), currentWorkingDirectory);
        }
        return NekoJSPaths.legacy().resolveNekoWritePathForCreate(String.valueOf(path), currentWorkingDirectory);
    }

    private Path resolveWriteExisting(String path) throws IOException {
        if (ClassFilter.isAllowFsWriteOutsideNekojs()) {
            return NekoJSPaths.legacy().resolveGamePath(String.valueOf(path), currentWorkingDirectory);
        }
        return NekoJSPaths.legacy().resolveNekoWritePath(String.valueOf(path), currentWorkingDirectory);
    }

    private Path verifyWriteParentForCreate(Path path) throws IOException {
        if (ClassFilter.isAllowFsWriteOutsideNekojs()) {
            return NekoJSPaths.legacy().verifyInsideGameDirForCreate(path);
        }
        return NekoJSPaths.legacy().verifyInsideNekoRootForCreate(path);
    }
}
