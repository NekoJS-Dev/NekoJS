import java.lang.reflect.*;
import java.util.Comparator;
import java.util.function.Consumer;
public class Pr65SamAudit {
    interface StringConsumer extends Consumer<String> {}
    static Method proposedSam(Class<?> cls) {
        if (!cls.isInterface()) return null;
        Method found = null;
        for (Method m : cls.getMethods()) {
            if (m.isDefault() || Modifier.isStatic(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (found != null) return null;
            found = m;
        }
        return found;
    }
    public static void main(String[] args) {
        System.out.println("Comparator SAM: " + proposedSam(Comparator.class));
        Method child = proposedSam(StringConsumer.class);
        System.out.println("StringConsumer own type parameters: " + java.util.Arrays.toString(StringConsumer.class.getTypeParameters()));
        System.out.println("StringConsumer SAM parameter: " + child.getGenericParameterTypes()[0]);
        System.out.println("StringConsumer declared parent: " + StringConsumer.class.getGenericInterfaces()[0]);
    }
}