package group.flyfish.dev.common.constants;

public interface CommonConstant {

    /**
     * 正常状态
     */
    Integer STATUS_NORMAL = 0;

    /**
     * 禁用状态
     */
    Integer STATUS_DISABLE = -1;

    /**
     * 删除标志
     */
    Integer DEL_FLAG_1 = 1;

    /**
     * 未删除
     */
    Integer DEL_FLAG_0 = 0;

    /**
     * 系统日志类型： 登录
     */
    int LOG_TYPE_1 = 1;

    /**
     * 系统日志类型： 操作
     */
    int LOG_TYPE_2 = 2;

    /**
     * 操作日志类型： 查询
     */
    int OPERATE_TYPE_1 = 1;

    /**
     * 操作日志类型： 添加
     */
    int OPERATE_TYPE_2 = 2;

    /**
     * 操作日志类型： 更新
     */
    int OPERATE_TYPE_3 = 3;

    /**
     * 操作日志类型： 删除
     */
    int OPERATE_TYPE_4 = 4;

    /**
     * 操作日志类型： 倒入
     */
    int OPERATE_TYPE_5 = 5;

    /**
     * 操作日志类型： 导出
     */
    int OPERATE_TYPE_6 = 6;


    /**
     * {@code 500 Server Error} (HTTP/1.0 - RFC 1945)
     */
    Integer SC_INTERNAL_SERVER_ERROR_500 = 500;
    /**
     * {@code 200 OK} (HTTP/1.0 - RFC 1945)
     */
    Integer SC_OK_200 = 200;

    /**
     * 用户过期
     */
    Integer SC_USER_PASSWORD_EXPIRED = 507;

    /**
     * 用户密码不安全
     */
    Integer SC_USER_NOT_SAFE = 508;

    /**
     * 访问权限认证未通过 510
     */
    Integer SC_JEECG_NO_AUTHZ = 510;

    /**
     * 登录用户Shiro权限缓存KEY前缀
     */
    public static String PREFIX_USER_SHIRO_CACHE = "shiro:cache:org.jeecg.modules.shiro.authc.ShiroRealm.authorizationCache:";
    /**
     * 登录用户Token令牌缓存KEY前缀
     */
    String PREFIX_USER_TOKEN = "prefix_user_token_";
    /**
     * Token缓存时间：3600秒即一小时
     */
    int TOKEN_EXPIRE_TIME = 3600;


    /**
     * 0：一级菜单
     */
    Integer MENU_TYPE_0 = 0;
    /**
     * 1：子菜单
     */
    Integer MENU_TYPE_1 = 1;
    /**
     * 2：按钮权限
     */
    Integer MENU_TYPE_2 = 2;

    /**
     * 通告对象类型（USER:指定用户，ALL:全体用户）
     */
    String MSG_TYPE_UESR = "USER";
    String MSG_TYPE_ALL = "ALL";

    /**
     * 发布状态（0未发布，1已发布，2已撤销）
     */
    String NO_SEND = "0";
    String HAS_SEND = "1";
    String HAS_CANCLE = "2";

    /**
     * 阅读状态（0未读，1已读）
     */
    String HAS_READ_FLAG = "1";
    String NO_READ_FLAG = "0";

    /**
     * 优先级（L低，M中，H高）
     */
    String PRIORITY_L = "L";
    String PRIORITY_M = "M";
    String PRIORITY_H = "H";

    /**
     * 短信模板方式  0 .登录模板、1.注册模板、2.忘记密码模板
     */
    String SMS_TPL_TYPE_0 = "0";
    String SMS_TPL_TYPE_1 = "1";
    String SMS_TPL_TYPE_2 = "2";

    /**
     * 状态(0无效1有效)
     */
    String STATUS_0 = "0";
    String STATUS_1 = "1";

    /**
     * 启用和禁用状态
     */
    int ENABLE = 1;
    int DISABLE = 0;

    /**
     * 同步工作流引擎1同步0不同步
     */
    String ACT_SYNC_0 = "0";
    String ACT_SYNC_1 = "1";

    /**
     * 消息类型1:通知公告2:系统消息
     */
    String MSG_CATEGORY_1 = "1";
    String MSG_CATEGORY_2 = "2";

    /**
     * 是否配置菜单的数据权限 1是0否
     */
    Integer RULE_FLAG_0 = 0;
    Integer RULE_FLAG_1 = 1;

    /**
     * 是否用户已被冻结 1(解冻)正常 2冻结
     */
    Integer USER_UNFREEZE = 1;
    Integer USER_FREEZE = 2;

    /**
     * 字典翻译文本后缀
     */
    String DICT_TEXT_SUFFIX = "_dictText";

    /**
     * 表单设计器主表类型
     */
    Integer DESIGN_FORM_TYPE_MAIN = 1;

    /**
     * 表单设计器子表表类型
     */
    Integer DESIGN_FORM_TYPE_SUB = 2;

    /**
     * 表单设计器URL授权通过
     */
    Integer DESIGN_FORM_URL_STATUS_PASSED = 1;

    /**
     * 表单设计器URL授权未通过
     */
    Integer DESIGN_FORM_URL_STATUS_NOT_PASSED = 2;

    /**
     * 表单设计器URL授权未通过
     */
    String DESIGN_FORM_URL_TYPE_ADD = "add";
    /**
     * 表单设计器URL授权未通过
     */
    String DESIGN_FORM_URL_TYPE_EDIT = "edit";
    /**
     * 表单设计器URL授权未通过
     */
    String DESIGN_FORM_URL_TYPE_DETAIL = "detail";
    /**
     * 表单设计器URL授权未通过
     */
    String DESIGN_FORM_URL_TYPE_VIEW = "view";
    /**
     * 未定义（js）
     */
    String UNDEFINED = "undefined";

    String MESSAGE_SAVE_ERROR = "尝试在数据库新建行时发生未知异常！保存失败！";

    String MESSAGE_UPDATE_ERROR = "尝试更新数据库行时发生未知异常！修改失败！";

    String MESSAGE_DELETE_ERROR = "尝试删除数据库行时发生未知异常！";

    String MESSAGE_RECORD_NOT_FOUND = "未找到要更新的行！";

    /**
     * 文件类型 1：文件 2：文件夹
     */
    String FILE_1 = "1";
    String FILE_2 = "2";
}
