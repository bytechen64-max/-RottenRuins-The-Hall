import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/** Source-only boundary checks. Uses the JDK parser, without game dependencies. */
public final class ArchitectureCheck {
    static final String ROOT = "org.bytechen.rottenruins.";
    static final Set<String> LAYERS = Set.of("advancement", "api", "client", "command", "compat", "config",
            "content", "core", "datagen", "event", "gameplay", "gametest", "mixin", "network", "overworld", "platform", "utils");
    record Source(String path, String name, String pkg, Set<String> refs, Set<String> ids, List<String> stars) {}
    final Map<String, Source> sources = new TreeMap<>();
    final Set<String> debt = new TreeSet<>();
    final List<String> errors = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        ArchitectureCheck check = new ArchitectureCheck();
        Path root = Path.of(args[0]);
        check.read(root);
        check.inspect();
        Path ledger = Path.of(args[1]);
        Set<String> allowed = Files.readAllLines(ledger).stream().filter(s -> !s.isBlank() && !s.startsWith("#"))
                .collect(Collectors.toCollection(TreeSet::new));
        check.validateLedger(allowed);
        if (!check.errors.isEmpty()) throw new IllegalStateException(String.join("\n", check.errors));
        Map<String, Long> coverage = check.sources.values().stream().collect(Collectors.groupingBy(
                s -> s.path.contains("/") ? s.path.substring(0, s.path.indexOf('/')) : "bootstrap", TreeMap::new, Collectors.counting()));
        System.out.println("Architecture source coverage: " + check.sources.size() + " Java files " + coverage);
        System.out.println("Reviewed coupling edges (not portable/side-safety certification): " + allowed.size());
    }

    void validateLedger(Set<String> allowed) {
        for (String edge : debt) if (!allowed.contains(edge)) errors.add("Unreviewed platform/client coupling: " + edge);
        for (String edge : allowed) if (!debt.contains(edge)) errors.add("Remove stale coupling entry: " + edge);
    }

    void read(Path root) throws Exception {
        List<Path> paths;
        try (var files = Files.walk(root)) {
            paths = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !Set.of("mexin", "mexinlauncher").contains(root.relativize(p).getName(0).toString())).sorted().toList();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(null, fm, diagnostics, List.of("-proc:none", "--release", "17"), null,
                    fm.getJavaFileObjectsFromPaths(paths));
            for (CompilationUnitTree unit : task.parse()) {
                String path = root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                if (path.contains("/") && !LAYERS.contains(path.substring(0, path.indexOf('/')))) errors.add("Unclassified source: " + path);
                String pkg = unit.getPackageName().toString();
                String name = pkg + "." + Path.of(path).getFileName().toString().replace(".java", "");
                Set<String> refs = new TreeSet<>(), ids = new TreeSet<>();
                List<String> stars = new ArrayList<>();
                for (ImportTree imp : unit.getImports()) {
                    String ref = imp.getQualifiedIdentifier().toString();
                    if (path.startsWith("network/") && client(ref)) errors.add("Common network must not import clients: " + path + " -> " + ref);
                    if (ref.endsWith(".*")) stars.add(ref.substring(0, ref.length() - 2));
                    refs.add(typeName(ref));
                }
                new TreeScanner<Void, Void>() {
                    int clientGuardDepth;
                    @Override public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                        String method = node.getMethodSelect().toString();
                        if (method.equals("net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn") && node.getArguments().size() == 2
                                && node.getArguments().get(0).toString().equals("net.minecraftforge.api.distmarker.Dist.CLIENT")) {
                            scan(node.getMethodSelect(), unused);
                            scan(node.getArguments().get(0), unused);
                            clientGuardDepth++;
                            scan(node.getArguments().get(1), unused);
                            clientGuardDepth--;
                            return null;
                        }
                        return super.visitMethodInvocation(node, unused);
                    }
                    @Override public Void visitImport(ImportTree node, Void unused) { return null; }
                    @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                        ids.add(node.getName().toString()); return super.visitIdentifier(node, unused);
                    }
                    @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                        ids.add(node.getIdentifier().toString());
                        String ref = node.toString();
                        if (path.startsWith("network/") && client(ref) && clientGuardDepth == 0)
                            errors.add("Unguarded common network client access: " + path + " -> " + ref);
                        if ((ref.startsWith(ROOT) || ref.startsWith("net.minecraft.") || ref.startsWith("net.minecraftforge."))
                                && Arrays.stream(ref.split("\\.")).anyMatch(p -> !p.isEmpty() && Character.isUpperCase(p.charAt(0)))) refs.add(typeName(ref));
                        return super.visitMemberSelect(node, unused);
                    }
                }.scan(unit.getTypeDecls(), null);
                sources.put(name, new Source(path, name, pkg, refs, ids, stars));
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) if (diagnostic.getKind() == Diagnostic.Kind.ERROR) errors.add(diagnostic.toString());
        // Resolve implicit same-package and wildcard project references as well as explicit imports.
        for (Source s : sources.values()) for (String id : s.ids) {
            if (sources.containsKey(s.pkg + "." + id)) s.refs.add(s.pkg + "." + id);
            for (String star : s.stars) if (sources.containsKey(star + "." + id)) s.refs.add(star + "." + id);
        }
    }

    static String typeName(String ref) {
        String[] parts = ref.split("\\.");
        for (int i = 0; i < parts.length; i++) if (!parts[i].isEmpty() && Character.isUpperCase(parts[i].charAt(0)))
            return String.join(".", Arrays.copyOf(parts, i + 1));
        return ref;
    }
    static boolean in(String ref, String layer) { return ref.startsWith(ROOT + layer + "."); }
    static boolean client(String ref) { return in(ref, "client") || ref.startsWith("net.minecraft.client.") || ref.startsWith("net.minecraftforge.client.") || ref.startsWith("com.mojang.blaze3d."); }

    void inspect() {
        for (Source s : sources.values()) {
            for (String ref : s.refs) {
                if (in(s.name, "api") && ((ref.startsWith(ROOT) && !in(ref, "api") && !in(ref, "core")) || client(ref)))
                    errors.add("API implementation dependency: " + s.path + " -> " + ref);
                if (!in(s.name, "client") && client(ref)) debt.add("common-client | " + s.path + " -> " + ref);
                if (in(s.name, "client") && ref.startsWith("net.minecraftforge.")) debt.add("client-forge | " + s.path + " -> " + ref);
                if (in(s.name, "utils") && (ref.startsWith("net.minecraftforge.") || in(ref, "platform"))) debt.add("utility-platform | " + s.path + " -> " + ref);
            }
            if (in(s.name, "gameplay")) visitGameplay(s, new LinkedHashSet<>(), s.path);
        }
    }

    void visitGameplay(Source s, Set<String> seen, String chain) {
        if (!seen.add(s.name)) return;
        if (s.ids.contains("getPersistentData") || s.ids.contains("PERSISTED_NBT_TAG")) errors.add("Gameplay persistence extension: " + chain);
        for (String ref : s.refs) {
            boolean forbidden = ref.startsWith("net.minecraftforge.") || client(ref) ||
                    (ref.startsWith(ROOT) && !in(ref, "core") && !in(ref, "api") && !in(ref, "gameplay") && !in(ref, "utils"));
            if (forbidden) errors.add("Gameplay implementation dependency: " + chain + " -> " + ref);
            // Public API events may themselves extend Forge events; only traverse implementation helpers.
            if ((in(ref, "utils") || in(ref, "gameplay")) && sources.containsKey(ref)) visitGameplay(sources.get(ref), seen, chain + " -> " + sources.get(ref).path);
        }
    }
}
