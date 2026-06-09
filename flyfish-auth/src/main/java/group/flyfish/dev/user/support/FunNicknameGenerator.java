package group.flyfish.dev.user.support;

import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 趣味昵称生成器。
 *
 * <p>微信服务号验证码登录通常只能稳定拿到 openid，昵称和头像取决于微信接口是否授权返回。为了避免界面里
 * 出现一排“微信用户 #xxxxxx”，这里采用成熟的 Petname/Docker codename 思路：用稳定用户标识计算哈希，
 * 再映射到人工维护的中文词表，生成可读、可记、同一用户稳定不变的昵称。</p>
 *
 * <p>本类不调用外部服务，登录链路不会受到第三方接口限流、网络波动或服务下线影响。</p>
 */
public final class FunNicknameGenerator {

    private static final String VERSION = "flyfish-fun-nickname-v1";

    private static final String[] TRAITS = {
            "爱吃山药", "爱喝乌龙", "会写代码", "喜欢晚风", "收藏星光", "会调接口",
            "爱看文档", "追着灵感", "爱逛小铺", "喜欢清晨", "守着月光", "会画原型",
            "整理清单", "爱听雨声", "懂点设计", "喜欢薄荷", "会修问题", "擅长复盘",
            "抱着热茶", "爱看日落", "偏爱雪糕", "会搭积木", "喜欢海盐", "带着好奇",
            "爱写便签", "守护进度", "会找线索", "喜欢桂花", "点亮灵感", "认真冒泡"
    };

    private static final String[] COLORS = {
            "蓝", "青", "橙", "银", "紫", "绿", "暖", "白", "金", "墨",
            "晴", "松", "海", "云", "月", "栗", "桃", "雾", "竹", "星"
    };

    private static final String[] ANIMALS = {
            "小熊", "小鹿", "小猫", "小狐", "小兔", "小鲸", "小鹤", "小豹", "小海豚", "小企鹅",
            "小松鼠", "小树懒", "小飞象", "小锦鲤", "小海豹", "小考拉", "小熊猫", "小夜莺", "小海鸥", "小海星"
    };

    private FunNicknameGenerator() {
    }

    /**
     * 根据稳定标识生成趣味昵称。
     *
     * @param stableKey openid、unionId、用户 ID 等稳定标识
     * @return 趣味昵称，例如“爱吃山药的蓝小熊”
     */
    public static String generate(String stableKey) {
        byte[] digest = digest(StringUtils.defaultIfBlank(stableKey, "anonymous"));
        String trait = TRAITS[index(digest, 0, TRAITS.length)];
        String color = COLORS[index(digest, 8, COLORS.length)];
        String animal = ANIMALS[index(digest, 16, ANIMALS.length)];
        return trait + "的" + color + animal;
    }

    /**
     * 判断旧版微信兜底昵称是否需要替换。
     */
    public static boolean isGenericWechatName(String value) {
        String text = StringUtils.trimToEmpty(value);
        return "微信用户".equals(text)
                || StringUtils.startsWith(text, "微信用户 #")
                || StringUtils.startsWith(text, "微信用户-")
                || StringUtils.startsWith(text, "微信用户 ")
                || StringUtils.startsWith(text, "微信-");
    }

    private static byte[] digest(String stableKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VERSION.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            return digest.digest(stableKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static int index(byte[] digest, int offset, int length) {
        long value = ByteBuffer.wrap(digest, offset, Long.BYTES).getLong();
        return Math.floorMod(value, length);
    }
}
