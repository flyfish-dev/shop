package group.flyfish.dev.common.bean.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleKeyValue {

    private String key;

    private String value;
}
