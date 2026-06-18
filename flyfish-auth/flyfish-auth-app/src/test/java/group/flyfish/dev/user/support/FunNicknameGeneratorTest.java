package group.flyfish.dev.user.support;

import group.flyfish.dev.auth.api.user.FunNicknameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunNicknameGeneratorTest {

    @Test
    void shouldGenerateStableReadableNickname() {
        String first = FunNicknameGenerator.generate("ojVas5_u3yFznFu5-DDIkDP5ZuU4");
        String second = FunNicknameGenerator.generate("ojVas5_u3yFznFu5-DDIkDP5ZuU4");

        assertThat(first)
                .isEqualTo(second)
                .contains("的")
                .doesNotContain("#")
                .hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void shouldRecognizeLegacyWechatFallbackNames() {
        assertThat(FunNicknameGenerator.isGenericWechatName("微信用户")).isTrue();
        assertThat(FunNicknameGenerator.isGenericWechatName("微信用户 #P5ZuU4")).isTrue();
        assertThat(FunNicknameGenerator.isGenericWechatName("微信用户-23")).isTrue();
        assertThat(FunNicknameGenerator.isGenericWechatName("微信-openid")).isTrue();
        assertThat(FunNicknameGenerator.isGenericWechatName("爱吃山药的蓝小熊")).isFalse();
    }
}
