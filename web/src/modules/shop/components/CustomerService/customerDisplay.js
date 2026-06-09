const GENERIC_WECHAT_NAME = '微信用户';

const text = value => String(value || '').trim();

const isGenericWechatName = value => {
  const name = text(value);
  return name === GENERIC_WECHAT_NAME
    || name.startsWith(`${GENERIC_WECHAT_NAME} #`)
    || name.startsWith(`${GENERIC_WECHAT_NAME}-`)
    || name.startsWith(`${GENERIC_WECHAT_NAME} `)
    || name.startsWith('微信-');
};

const traits = [
  '爱吃山药', '爱喝乌龙', '会写代码', '喜欢晚风', '收藏星光', '会调接口',
  '爱看文档', '追着灵感', '爱逛小铺', '喜欢清晨', '守着月光', '会画原型',
  '整理清单', '爱听雨声', '懂点设计', '喜欢薄荷', '会修问题', '擅长复盘',
  '抱着热茶', '爱看日落', '偏爱雪糕', '会搭积木', '喜欢海盐', '带着好奇',
  '爱写便签', '守护进度', '会找线索', '喜欢桂花', '点亮灵感', '认真冒泡'
];

const colors = [
  '蓝', '青', '橙', '银', '紫', '绿', '暖', '白', '金', '墨',
  '晴', '松', '海', '云', '月', '栗', '桃', '雾', '竹', '星'
];

const animals = [
  '小熊', '小鹿', '小猫', '小狐', '小兔', '小鲸', '小鹤', '小豹', '小海豚', '小企鹅',
  '小松鼠', '小树懒', '小飞象', '小锦鲤', '小海豹', '小考拉', '小熊猫', '小夜莺', '小海鸥', '小海星'
];

const hash = value => {
  let next = 2166136261;
  for (const char of `flyfish-fun-nickname-v1:${value || 'anonymous'}`) {
    next ^= char.codePointAt(0);
    next = Math.imul(next, 16777619);
  }
  return next >>> 0;
};

const generatedWechatName = source => {
  const key = text(source?.wechatOpenid || source?.openid || source?.userId || 'anonymous');
  const value = hash(key);
  return `${traits[value % traits.length]}的${colors[Math.floor(value / 31) % colors.length]}${animals[Math.floor(value / 131) % animals.length]}`;
};

const shortIdentity = source => {
  const openid = text(source?.wechatOpenid || source?.openid);
  if (openid && !openid.startsWith('web:user:')) {
    return openid.length <= 6 ? openid : openid.slice(-6);
  }
  const userId = source?.userId;
  return userId ? `U${userId}` : '';
};

export const customerDisplayName = source => {
  const name = text(source?.displayName || source?.senderName);
  if (name && !isGenericWechatName(name)) {
    return name;
  }
  if (source?.wechatOpenid || source?.openid || source?.userId) {
    return generatedWechatName(source);
  }
  const suffix = shortIdentity(source);
  if (suffix) {
    return `${GENERIC_WECHAT_NAME} #${suffix}`;
  }
  return name || '客户';
};

export const customerSenderName = (message, conversation) => customerDisplayName({
  ...conversation,
  userId: message?.userId || conversation?.userId,
  displayName: message?.senderName || conversation?.displayName
});
