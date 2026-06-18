package group.flyfish.dev.ddl.compare.bean;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 双值元祖
 *
 * @author wangyu
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class CompareTuple<V1, V2> {

    private final V1 previous;

    private final V2 current;

    public static <V1, V2> CompareTuple<V1, V2> of(V1 v1, V2 v2) {
        return new CompareTuple<>(v1, v2);
    }
}
