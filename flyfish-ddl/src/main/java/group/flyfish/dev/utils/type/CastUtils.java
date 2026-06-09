package group.flyfish.dev.utils.type;

public interface CastUtils {

    /**
     * 强制转换类型
     *
     * @param t   入参类型
     * @param <T> 入参泛型
     * @param <R> 出参泛型
     * @return 出参
     */
    @SuppressWarnings("unchecked")
    static <T, R> R cast(T t) {
        return (R) t;
    }
}
