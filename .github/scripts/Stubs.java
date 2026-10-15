import java.io.IOException;
import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodTransform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Development kit: copies every jar on the compile classpaths with the method bodies removed (signatures only, no code),
 * so the mod can be type-checked with javac outside of Gradle.
 *
 *     java Stubs.java <classpaths file> <output dir>
 *
 * The classpaths file has lines like "MAIN /path/to/some.jar" (from the printClasspaths task). The output dir gets one
 * stub jar per input jar plus classpath.txt, which lists "MAIN some.jar" lines in the same order.
 */
public final class Stubs {

	public static void main(String[] args) throws IOException {
		List<String> lines = Files.readAllLines(Path.of(args[0]));
		Path out = Path.of(args[1]);
		Files.createDirectories(out);
		Map<String, String> names = new LinkedHashMap<>();
		StringBuilder index = new StringBuilder();
		for (String line : lines) {
			int space = line.indexOf(' ');
			if (space < 0) {
				continue;
			}
			String set = line.substring(0, space);
			String path = line.substring(space + 1).trim();
			if (!set.matches("[A-Z]+")) {
				continue;
			}
			if (!path.endsWith(".jar")) {
				index.append(set).append(" DIR ").append(path).append('\n');
				continue;
			}
			String name = names.get(path);
			if (name == null) {
				name = unique(Path.of(path).getFileName().toString(), names);
				names.put(path, name);
				strip(Path.of(path), out.resolve(name));
			}
			index.append(set).append(' ').append(name).append('\n');
		}
		Files.writeString(out.resolve("classpath.txt"), index);
	}

	private static String unique(String file, Map<String, String> taken) {
		String name = file;
		int n = 2;
		while (taken.containsValue(name)) {
			name = file.replace(".jar", "-" + n++ + ".jar");
		}
		return name;
	}

	private static void strip(Path in, Path target) throws IOException {
		ClassFile cf = ClassFile.of(ClassFile.DebugElementsOption.DROP_DEBUG, ClassFile.LineNumbersOption.DROP_LINE_NUMBERS);
		ClassTransform noCode = ClassTransform.transformingMethods(MethodTransform.dropping(e -> e instanceof CodeModel));
		int classes = 0;
		int kept = 0;
		try (ZipFile zip = new ZipFile(in.toFile()); OutputStream file = Files.newOutputStream(target);
			 ZipOutputStream jar = new ZipOutputStream(file)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.endsWith("module-info.class")) {
					continue;
				}
				byte[] bytes = zip.getInputStream(entry).readAllBytes();
				byte[] stub;
				try {
					ClassModel model = cf.parse(bytes);
					stub = cf.transformClass(model, noCode);
					classes++;
				} catch (RuntimeException e) {
					stub = bytes; // couldn't be rewritten: keep it as it is
					kept++;
				}
				jar.putNextEntry(new ZipEntry(name));
				jar.write(stub);
				jar.closeEntry();
			}
		}
		System.out.println(target.getFileName() + ": " + classes + " stubbed" + (kept > 0 ? ", " + kept + " kept" : ""));
	}
}
