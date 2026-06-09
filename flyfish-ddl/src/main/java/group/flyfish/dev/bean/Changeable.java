package group.flyfish.dev.bean;

import group.flyfish.dev.utils.type.CastUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 表示可修改的
 *
 * @author wangyu
 */
public abstract class Changeable<T> implements Comparable<T> {

    @Getter
    @Setter
    @ApiModelProperty("旧名称")
    private String oldName;

    // 标记已变更，手动指定的flag，用于复用逻辑
    private transient boolean marked;

    // 创建中标记
    @JsonIgnore
    @Getter
    private transient boolean creating;

    public T markCreating() {
        this.creating = true;
        return CastUtils.cast(this);
    }

    public abstract String getName();

    public boolean nameChanged() {
        return StringUtils.isNotBlank(oldName) && !oldName.equals(getName());
    }

    public String wrappedName() {
        String name = getName();
        if (StringUtils.isNotBlank(name)) {
            return "`" + getName() + "`";
        }
        return null;
    }

    public String previousName() {
        if (StringUtils.isNotBlank(oldName)) {
            return oldName;
        }
        return getName();
    }

    public void markChanged() {
        marked = true;
    }

    public boolean marked() {
        return this.marked;
    }
}
