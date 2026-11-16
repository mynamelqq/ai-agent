package generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 
 * @TableName users
 */
@TableName(value ="users")
@Data
public class Users implements Serializable {
    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password_hash;

    /**
     * 头像
     */
    private String avatar_url;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 状态：1正常 0封禁
     */
    private Integer status;

    /**
     * 上次登录时间
     */
    private Date last_login_at;

    /**
     * 
     */
    private Date created_at;

    /**
     * 
     */
    private Integer daily_token_limit;

    /**
     * 
     */
    private String ip;

    /**
     * 
     */
    private Long total_tokens_used;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}