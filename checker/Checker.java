import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Checker {
    static String normalized(String s) { return s.trim().replaceAll("\\s+", " "); }
    static String shortText(String s) { return s.length() > 500 ? s.substring(0, 500) + "…" : s; }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[2]);
        Path dir = root.resolve("tests/" + args[0]);
        List<Path> inputs;
        try (var files = Files.list(dir)) {
            inputs = files.filter(p -> p.toString().endsWith(".in.txt")).sorted().toList();
        }
        if (inputs.isEmpty()) throw new IllegalStateException("Нет тестов: " + dir);
        int passed = 0;
        for (Path input : inputs) {
            String name = input.getFileName().toString().replace(".in.txt", "");
            Path expectedFile = dir.resolve(name + ".out.txt");
            String expected = normalized(Files.readString(expectedFile));
            Path work = root.resolve("build/checkTask/" + args[0] + "/" + name);
            Files.createDirectories(work);
            // Также разрешаем ученику читать фиксированный файл input.txt.
            Files.copy(input, work.resolve("input.txt"), StandardCopyOption.REPLACE_EXISTING);
            Path out = work.resolve("actual.txt"), err = work.resolve("stderr.txt");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            Process p = new ProcessBuilder(java, "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", args[1], "MainKt")
                .directory(work.toFile()).redirectInput(input.toFile())
                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            String failure = null;
            while (!p.waitFor(20, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() > deadline) { failure = "превышено время (3 с)"; break; }
                if (Files.size(out) + Files.size(err) > 1_000_000) { failure = "слишком много вывода"; break; }
            }
            if (failure != null) { p.destroyForcibly(); p.waitFor(); }
            else if (p.exitValue() != 0) failure = "ошибка выполнения: " + shortText(Files.readString(err));
            else if (Files.size(out) > 1_000_000) failure = "слишком много вывода";
            else {
                String actual = normalized(Files.readString(out));
                if (!actual.equals(expected)) failure = "ожидалось: " + shortText(expected) + "; получено: " + shortText(actual);
            }
            if (failure == null) { passed++; System.out.println("Тест " + name + ": OK"); }
            else System.out.println("Тест " + name + ": ОШИБКА — " + failure);
        }
        System.out.println("Пройдено: " + passed + " из " + inputs.size());
        if (passed != inputs.size()) System.exit(1);
    }
}
