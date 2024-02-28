package util;

public class Tuple<T, T1> {

    private final T var1;

    public T getVar1() {
        return var1;
    }

    public T1 getVar2() {
        return var2;
    }

    private final T1 var2;

    public Tuple(T var1, T1 var2) {
        this.var1 = var1;
        this.var2 = var2;
    }

    public static <T,T1> Tuple<T,T1> of(T t, T1 t1) {
        return new Tuple<>(t, t1);
    }
}
