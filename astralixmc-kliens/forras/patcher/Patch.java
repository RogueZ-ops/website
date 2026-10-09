import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Usage: java Patch <groupDir> <oldVersion> <newVersion> */
public class Patch implements Opcodes {
   static final String T = "hu/astralixmc/hud/ui/Transitions";
   static int changes;

   public static void main(String[] a) throws Exception {
      Path root = Paths.get(a[0]);
      String oldV = a[1], newV = a[2];
      patch(root, "hu/astralixmc/hud/ui/TitleUi.class", Patch::titleUi, 2);
      patch(root, "hu/astralixmc/hud/module/BrandModule.class", Patch::brand, 1);
      patch(root, "hu/astralixmc/hud/Hud.class", Patch::hud, 2);
      patch(root, "hu/astralixmc/hud/platform/ExtraImpl.class", Patch::extra, 1);
      // version bump in every class string constant
      try (var s = Files.walk(root.resolve("hu"))) {
         for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".class"))::iterator) {
            byte[] in = Files.readAllBytes(p);
            if (!new String(in, "ISO-8859-1").contains(oldV)) continue;
            ClassReader cr = new ClassReader(in);
            ClassWriter cw = new ClassWriter(0);
            int[] n = {0};
            cr.accept(new ClassVisitor(ASM9, cw) {
               Object fix(Object o) {
                  if (o instanceof String str && str.contains(oldV)) { n[0]++; return str.replace(oldV, newV); }
                  return o;
               }
               @Override public FieldVisitor visitField(int acc, String name, String d, String sig, Object v) {
                  return super.visitField(acc, name, d, sig, fix(v));
               }
               @Override public MethodVisitor visitMethod(int acc, String name, String d, String sig, String[] ex) {
                  return new MethodVisitor(ASM9, super.visitMethod(acc, name, d, sig, ex)) {
                     @Override public void visitLdcInsn(Object v) { super.visitLdcInsn(fix(v)); }
                     @Override public void visitInvokeDynamicInsn(String nm, String ds, Handle bsm, Object... args) {
                        Object[] b = args.clone();
                        for (int i = 0; i < b.length; i++) b[i] = fix(b[i]);
                        super.visitInvokeDynamicInsn(nm, ds, bsm, b);
                     }
                  };
               }
            }, 0);
            if (n[0] > 0) {
               Files.write(p, cw.toByteArray());
               System.out.println("  version " + root.relativize(p) + " x" + n[0]);
            }
         }
      }
   }

   interface Fn { int apply(ClassNode cn); }

   static void patch(Path root, String rel, Fn fn, int expected) throws Exception {
      Path p = root.resolve(rel);
      ClassNode cn = new ClassNode();
      new ClassReader(Files.readAllBytes(p)).accept(cn, ClassReader.EXPAND_FRAMES);
      int n = fn.apply(cn);
      if (n != expected) throw new IllegalStateException(rel + ": expected " + expected + " patch sites, got " + n);
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      cn.accept(cw);
      Files.write(p, cw.toByteArray());
      System.out.println("  patched " + rel + " (" + n + ")");
   }

   static MethodNode method(ClassNode cn, String name, String desc) {
      for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
      throw new IllegalStateException("missing " + cn.name + "." + name + desc);
   }

   static int titleUi(ClassNode cn) {
      int n = 0;
      // 1) "Eredeti Minecraft menü" gomb: Hud::openVanillaMenu -> Transitions::leave
      for (MethodNode m : cn.methods) {
         for (AbstractInsnNode i : m.instructions) {
            if (i instanceof InvokeDynamicInsnNode id) {
               for (int k = 0; k < id.bsmArgs.length; k++) {
                  if (id.bsmArgs[k] instanceof Handle h && h.getOwner().equals("hu/astralixmc/hud/Hud")
                        && h.getName().equals("openVanillaMenu") && h.getDesc().equals("()V")) {
                     id.bsmArgs[k] = new Handle(H_INVOKESTATIC, T, "leave", "()V", false);
                     n++;
                  }
               }
            }
         }
      }
      if (n != 1) throw new IllegalStateException("TitleUi vanilla button handle count " + n);
      // 2) overlay a draw() végén
      MethodNode d = method(cn, "draw", "()V");
      int r = 0;
      for (AbstractInsnNode i : d.instructions.toArray()) {
         if (i.getOpcode() == RETURN) {
            InsnList l = new InsnList();
            l.add(new VarInsnNode(ALOAD, 0));
            l.add(new MethodInsnNode(INVOKESTATIC, T, "overlay", "(Lhu/astralixmc/hud/ui/TitleUi;)V", false));
            d.instructions.insertBefore(i, l);
            r++;
         }
      }
      if (r < 1) throw new IllegalStateException("TitleUi.draw has no RETURN");
      return n + 1;
   }

   static int brand(ClassNode cn) {
      MethodNode m = method(cn, "layout", "(Lhu/astralixmc/hud/Gfx;Lhu/astralixmc/hud/module/Ctx;)Z");
      LabelNode go = new LabelNode();
      InsnList l = new InsnList();
      l.add(new VarInsnNode(ALOAD, 2));
      l.add(new MethodInsnNode(INVOKESTATIC, T, "hideBrand", "(Lhu/astralixmc/hud/module/Ctx;)Z", false));
      l.add(new JumpInsnNode(IFEQ, go));
      l.add(new InsnNode(ICONST_0));
      l.add(new InsnNode(IRETURN));
      l.add(go);
      l.add(new FrameNode(F_NEW, 3,
            new Object[]{cn.name, "hu/astralixmc/hud/Gfx", "hu/astralixmc/hud/module/Ctx"}, 0, new Object[0]));
      m.instructions.insert(l);
      return 1;
   }

   static int hud(ClassNode cn) {
      method(cn, "tick", "()V").instructions.insert(new MethodInsnNode(INVOKESTATIC, T, "tick", "()V", false));
      method(cn, "leaveVanillaMenu", "()V").instructions.insert(new MethodInsnNode(INVOKESTATIC, T, "returning", "()V", false));
      return 2;
   }

   /** openVanillaTitle(): előbb TitleScreen(true) (eredeti, beúszó animáció), utána a paraméter nélküli. */
   static int extra(ClassNode cn) {
      MethodNode m = method(cn, "openVanillaTitle", "()Z");
      List<MethodInsnNode> getCtor = new ArrayList<>(), newInst = new ArrayList<>();
      for (AbstractInsnNode i : m.instructions) {
         if (i instanceof MethodInsnNode mi) {
            if (mi.owner.equals("java/lang/Class") && mi.name.equals("getConstructor")) getCtor.add(mi);
            if (mi.owner.equals("java/lang/reflect/Constructor") && mi.name.equals("newInstance")) newInst.add(mi);
         }
      }
      if (getCtor.size() != 2 || newInst.size() != 2) throw new IllegalStateException("ExtraImpl pattern mismatch");
      for (int k = 0; k < 2; k++) {
         boolean withBool = k == 0; // first attempt: (boolean) true; fallback: ()
         // argument array of getConstructor: from the instruction after the LDC <TitleScreen> up to the call
         AbstractInsnNode ldc = getCtor.get(k).getPrevious();
         while (!(ldc instanceof LdcInsnNode)) ldc = ldc.getPrevious();
         replaceBetween(m, ldc, getCtor.get(k), arr("java/lang/Class", withBool ? fieldGet("TYPE", "Ljava/lang/Class;") : null));
         replaceBetween(m, getCtor.get(k), newInst.get(k), arr("java/lang/Object", withBool ? fieldGet("TRUE", "Ljava/lang/Boolean;") : null));
      }
      return 1;
   }

   static AbstractInsnNode fieldGet(String name, String desc) {
      return new FieldInsnNode(GETSTATIC, "java/lang/Boolean", name, desc);
   }

   static InsnList arr(String type, AbstractInsnNode elem) {
      InsnList l = new InsnList();
      l.add(new InsnNode(elem == null ? ICONST_0 : ICONST_1));
      l.add(new TypeInsnNode(ANEWARRAY, type));
      if (elem != null) {
         l.add(new InsnNode(DUP));
         l.add(new InsnNode(ICONST_0));
         l.add(elem);
         l.add(new InsnNode(AASTORE));
      }
      return l;
   }

   static void replaceBetween(MethodNode m, AbstractInsnNode from, AbstractInsnNode to, InsnList with) {
      AbstractInsnNode i = from.getNext();
      while (i != to) {
         AbstractInsnNode nx = i.getNext();
         if (i instanceof LabelNode || i instanceof LineNumberNode || i instanceof FrameNode)
            throw new IllegalStateException("unexpected node in array build");
         m.instructions.remove(i);
         i = nx;
      }
      m.instructions.insertBefore(to, with);
   }
}
