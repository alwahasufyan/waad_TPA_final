package com.waad.tba.modules.systembackup.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class BackupStorageService {

    private final Environment environment;

    @Value("${file.storage.local.base-dir:./storage/uploads}")
    private String uploadsBaseDir;

    public Path createWorkingDirectory(Path backupRoot, Long backupId) throws IOException {
        Path dir = backupRoot.resolve("work").resolve("backup-" + backupId).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    public Path uploadsPath() {
        return Path.of(uploadsBaseDir).toAbsolutePath().normalize();
    }

    public Path dumpDatabase(Path workingDir, String fileName, List<String> warnings) throws IOException, InterruptedException {
        Path dumpFile = workingDir.resolve(fileName).toAbsolutePath().normalize();
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "postgres");
        String password = environment.getProperty("spring.datasource.password", "");

        PgTarget target = parseJdbcUrl(jdbcUrl);
        List<String> command = List.of(
                "pg_dump",
                "-h", target.host(),
                "-p", String.valueOf(target.port()),
                "-U", username,
                "-F", "c",
                "-f", dumpFile.toString(),
                target.database()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        env.put("PGPASSWORD", password == null ? "" : password);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();
        if (exit != 0) {
            String safeOutput = output == null ? "" : output.replace(password == null ? "" : password, "[REDACTED]");
            throw new IllegalStateException("pg_dump failed. Ensure pg_dump is installed and reachable by the backend runtime. " + safeOutput);
        }
        warnings.add("تم إنشاء نسخة قاعدة البيانات عبر pg_dump من بيئة تشغيل الـ backend بدون استخدام docker.sock.");
        return dumpFile;
    }

    public void addPathToZip(ZipOutputStream zip, Path sourceRoot, String zipPrefix, List<String> warnings) throws IOException {
        if (!Files.exists(sourceRoot)) {
            warnings.add("مسار الملفات غير موجود: " + sourceRoot);
            return;
        }
        if (!Files.isDirectory(sourceRoot)) {
            warnings.add("مسار الملفات ليس مجلدًا: " + sourceRoot);
            return;
        }
        try (var stream = Files.walk(sourceRoot)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                Path relative = sourceRoot.relativize(path);
                String entryName = zipPrefix + "/" + relative.toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        }
    }

    public Path writeZip(Path target, ZipWriter writer) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
            writer.write(zip);
        }
        return target;
    }

    public boolean isWritableDirectory(Path path) {
        try {
            Files.createDirectories(path);
            Path probe = Files.createTempFile(path, ".waad-backup-probe", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long usableSpace(Path path) {
        try {
            Files.createDirectories(path);
            return Files.getFileStore(path).getUsableSpace();
        } catch (Exception e) {
            return null;
        }
    }

    private PgTarget parseJdbcUrl(String jdbcUrl) {
        try {
            String raw = jdbcUrl.replaceFirst("^jdbc:", "");
            URI uri = URI.create(raw);
            String db = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            if (db.isBlank()) {
                throw new IllegalArgumentException("Database name missing");
            }
            return new PgTarget(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 5432, db);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse PostgreSQL JDBC URL for backup", e);
        }
    }

    public String environmentName() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) return "default";
        return String.join(",", profiles).toLowerCase(Locale.ROOT);
    }

    public List<String> includedComponents(boolean database, boolean files) {
        List<String> components = new ArrayList<>();
        if (database) components.add("database");
        if (files) components.add("uploads");
        components.add("manifest");
        return components;
    }

    public interface ZipWriter {
        void write(ZipOutputStream zip) throws IOException;
    }

    // ===================== Restore support (BKP-4) =====================

    /** True if the archive contains a database dump entry. */
    public boolean archiveContainsDatabaseDump(Path archive) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
            return zip.getEntry("database/database.dump") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Read the manifest.json entry from the archive; returns null if unreadable. */
    public String readManifestFromArchive(Path archive) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                return null;
            }
            try (var in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes());
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Extract the database dump entry to a temp file for inspection; caller deletes the parent dir. */
    public Path extractDatabaseDump(Path archive, Path workDir) throws IOException {
        Files.createDirectories(workDir);
        Path out = workDir.resolve("database.dump").toAbsolutePath().normalize();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("database/database.dump");
            if (entry == null) {
                throw new IOException("Archive has no database dump entry");
            }
            try (var in = zip.getInputStream(entry)) {
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return out;
    }

    /**
     * Safe restore-readability check: `pg_restore --list` parses the dump's table of
     * contents WITHOUT touching any database. Returns exit code + captured output.
     */
    public ProcessResult pgRestoreList(Path dumpFile) {
        try {
            ProcessBuilder builder = new ProcessBuilder("pg_restore", "--list", dumpFile.toString());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            return new ProcessResult(exit, output);
        } catch (Exception e) {
            return new ProcessResult(-1, e.getMessage());
        }
    }

    /**
     * DEV-ONLY real restore into the live datasource. Guarded exclusively by callers
     * (danger zone). Uses pg_restore --clean --if-exists. Never call in production.
     */
    public ProcessResult pgRestoreInto(Path dumpFile) {
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");
        String username = environment.getProperty("spring.datasource.username", "postgres");
        String password = environment.getProperty("spring.datasource.password", "");
        PgTarget target = parseJdbcUrl(jdbcUrl);
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "pg_restore",
                    "-h", target.host(),
                    "-p", String.valueOf(target.port()),
                    "-U", username,
                    "-d", target.database(),
                    "--clean", "--if-exists", "--no-owner",
                    dumpFile.toString());
            builder.environment().put("PGPASSWORD", password == null ? "" : password);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            String safe = output == null ? "" : output.replace(password == null ? "" : password, "[REDACTED]");
            return new ProcessResult(exit, safe);
        } catch (Exception e) {
            return new ProcessResult(-1, e.getMessage());
        }
    }

    public record ProcessResult(int exitCode, String output) {
        public boolean ok() {
            return exitCode == 0;
        }
    }

    private record PgTarget(String host, int port, String database) {
    }
}
