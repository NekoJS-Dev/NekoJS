import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

public final class SourceMapDifferential {
    private record Case(String label, Path file, String authored, String generated) {}

    private static String randomText(Random random) {
        String[] tokens = {"a", "value++;", " ", "\t", "\r", "\n", "\r\n", "\uD83D\uDE00", "\uD835\uDC9C", "\u4E2D", "'\\n'", "\u2028", "\u2029", "\u0000", "\"", "\\"};
        StringBuilder text = new StringBuilder();
        int length = random.nextInt(90);
        for (int index = 0; index < length; index++) {
            text.append(tokens[random.nextInt(tokens.length)]);
        }
        return text.toString();
    }

    public static void main(String[] arguments) throws Exception {
        Path scope = Path.of(arguments[0]);
        Path gson = Path.of(arguments[1]);
        URL gsonUrl = gson.toUri().toURL();
        URL[] oldUrls = {scope.resolve("old-2bf28fa7.jar").toUri().toURL(), gsonUrl};
        URL[] newUrls = {scope.resolve("new-classes").toUri().toURL(), gsonUrl};
        String className = "com.tkisor.nekojs.core.compiler.NekoSourceMapBuilder";
        List<Case> cases = new ArrayList<>();
        String[] edgeTexts = {null, "", "\n", "\n\n", "a\n", "abc\n\n", "\r", "\r\n", "a\r\nb\r\n", "\uD83D\uDE00\n", "\uD835\uDC9C\r\n\u4E2D", "\uD83D", "\uDE00", "a\u2028b\u2029c", "\t\u0000\\\"'", "long final line\n\n\n"};
        Path[] paths = {null, Path.of("native.js"), Path.of("scripts", "..", "server_scripts", "\u4E2D\uD83D\uDE00.js"), Path.of("C:\\fixture\\..\\native.js")};
        for (int authoredIndex = 0; authoredIndex < edgeTexts.length; authoredIndex++) {
            for (int generatedIndex = 0; generatedIndex < edgeTexts.length; generatedIndex++) {
                cases.add(new Case("edge-" + authoredIndex + "-" + generatedIndex,
                        paths[(authoredIndex + generatedIndex) % paths.length],
                        edgeTexts[authoredIndex], edgeTexts[generatedIndex]));
            }
        }
        long seed = 0x534F555243454D41L;
        Random random = new Random(seed);
        for (int index = 0; index < 256; index++) {
            String authored = randomText(random);
            Path file = paths[index % paths.length];
            cases.add(new Case("random-exact-" + index, file, authored, authored));
            cases.add(new Case("random-transform-" + index, file, authored, randomText(random)));
            cases.add(new Case("random-expanded-" + index, file, authored, "prefix\n" + authored + "\nextra\n"));
        }
        cases.add(new Case("many-empty-final-lines", Path.of("lines.js"), "\n".repeat(200), "\n".repeat(200)));
        cases.add(new Case("moderate-native-lines", Path.of("lines.js"), "value++;\n".repeat(1000), "value++;\n".repeat(1000)));
        MessageDigest corpusDigest = MessageDigest.getInstance("SHA-256");
        int exact = 0;
        int transformed = 0;
        long oldNanos = 0;
        long newNanos = 0;
        try (URLClassLoader oldLoader = new URLClassLoader(oldUrls, ClassLoader.getPlatformClassLoader());
             URLClassLoader newLoader = new URLClassLoader(newUrls, ClassLoader.getPlatformClassLoader())) {
            Class<?> oldClass = Class.forName(className, true, oldLoader);
            Class<?> newClass = Class.forName(className, true, newLoader);
            if (oldClass.getClassLoader() != oldLoader || newClass.getClassLoader() != newLoader || oldClass == newClass) {
                throw new AssertionError("Class-loader isolation failed");
            }
            Method oldIdentity = oldClass.getMethod("identity", Path.class, String.class, String.class);
            Method newIdentity = newClass.getMethod("identity", Path.class, String.class, String.class);
            for (Case testCase : cases) {
                long started = System.nanoTime();
                String oldOutput = (String) oldIdentity.invoke(null, testCase.file(), testCase.authored(), testCase.generated());
                oldNanos += System.nanoTime() - started;
                started = System.nanoTime();
                String newOutput = (String) newIdentity.invoke(null, testCase.file(), testCase.authored(), testCase.generated());
                newNanos += System.nanoTime() - started;
                byte[] oldBytes = oldOutput.getBytes(StandardCharsets.UTF_8);
                byte[] newBytes = newOutput.getBytes(StandardCharsets.UTF_8);
                if (!oldOutput.equals(newOutput) || !Arrays.equals(oldBytes, newBytes)) {
                    Files.writeString(scope.resolve("mismatch-old.json"), oldOutput, StandardCharsets.UTF_8);
                    Files.writeString(scope.resolve("mismatch-new.json"), newOutput, StandardCharsets.UTF_8);
                    throw new AssertionError("Identity JSON mismatch: " + testCase.label());
                }
                byte[] label = testCase.label().getBytes(StandardCharsets.UTF_8);
                corpusDigest.update(ByteBuffer.allocate(4).putInt(label.length).array());
                corpusDigest.update(label);
                corpusDigest.update(ByteBuffer.allocate(4).putInt(oldBytes.length).array());
                corpusDigest.update(oldBytes);
                String authored = testCase.authored() == null ? "" : testCase.authored();
                String generated = testCase.generated() == null ? "" : testCase.generated();
                if (authored.equals(generated)) {
                    exact++;
                } else {
                    transformed++;
                }
            }
        }
        String report = "VERDICT=PASS\nPUBLIC_SEAM=identity(Path,String,String)\n"
                + "CLASSLOADER_PARENT=platform-only\nGSON=" + gson + "\n"
                + "FIXED_SEED=" + seed + "\nCASES=" + cases.size() + "\nEXACT_NORMALIZED_CASES=" + exact
                + "\nTRANSFORMED_CASES=" + transformed + "\nCOUNTEREXAMPLES=0\n"
                + "IDENTICAL_JSON_CORPUS_SHA256=" + HexFormat.of().formatHex(corpusDigest.digest()).toUpperCase()
                + "\nOLD_TOTAL_MS_DIAGNOSTIC=" + oldNanos / 1_000_000.0
                + "\nNEW_TOTAL_MS_DIAGNOSTIC=" + newNanos / 1_000_000.0
                + "\nTIMING_NOT_A_CONTROLLED_BENCHMARK=true\n";
        Files.writeString(scope.resolve("result.txt"), report, StandardCharsets.UTF_8);
        System.out.print(report);
    }
}
