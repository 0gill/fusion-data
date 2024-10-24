package zer0g.fusion.data;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class FobTypeGenerator
{
    final static class TypeBeingGenerated implements FusionObjectType<FusionObject>
    {
        private final Class<?> _javaClass;

        private TypeBeingGenerated(Class<?> javaClass) {
            _javaClass = Objects.requireNonNull(javaClass);
        }

        @Override
        public String name() {
            return _javaClass.getName();
        }


        @Override
        public Validator validatorFor(String algName) throws IllegalArgumentException {
            throw new UnsupportedOperationException();
        }

        @Override
        public FusionObject read(Reader wire) throws IOException, ArithmeticException, ClassCastException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void write(Writer wire, FusionObject value) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Class<? extends FusionObjectBase> javaDataClass() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FusionObject make() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FusionObject makeKey() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FusionObjectSchema schema() {
            throw new UnsupportedOperationException();
        }
    }

    public static void main(String[] args) {
        new FobTypeGenerator(Path.of(args[0]).toFile()).generate();
    }

    private final File _classDir;

    public FobTypeGenerator(File classDir) {
        if (!classDir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + classDir);
        }
        _classDir = classDir;
    }

    public void generate() {
        var fobClasses = new ArrayList<Class>();
        gatherFobClasses(_classDir, fobClasses);
        for (Class fobClass : fobClasses) {
            Fusion.registerFobType(new TypeBeingGenerated(fobClass));
        }
        int failcount = 0;
        for (Class fobClass : fobClasses) {
            try {
                Fusion.generateFobType(fobClass, _classDir);
            } catch (Exception e) {
                failcount++;
                System.err.println("Failed to generate fob-type for " + fobClass.getName() + " because: " + e);
                e.printStackTrace();
            }
        }
        if (failcount > 0) {
            throw new RuntimeException("Failed to generate " + failcount + " fob-types!");
        }
    }

    private void gatherFobClasses(File dir, Collection<Class> result) {
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                gatherFobClasses(file, result);
            } else if (file.isFile() && file.getName().endsWith(".class") &&
                       !file.getName().endsWith(Fusion.FOB_TYPE_CLASS_NAME_SUFFIX + ".class"))
            {
                var path = _classDir.toPath().relativize(file.toPath()).toString();
                var name = path.substring(0, path.length() - ".class".length()).replace(File.separatorChar, '.');
                try {
                    var c = Class.forName(name);
                    if (c.isAnnotationPresent(FoType.class)) {
                        var fobtypeFile = new File(file.getParentFile(), Fusion.fobTypeClassBaseNameFor(c) + ".class");
                        if (fobtypeFile.isFile() && fobtypeFile.lastModified() >= file.lastModified()) {
                            System.out.println("No need to regenerate fob-type: " + fobtypeFile);
                            continue;
                        }
                        result.add(c);
                    }
                } catch (ClassNotFoundException | RuntimeException e) {
                    System.err.println("Could not load class: " + name);
                }
            }
        }
    }
}
